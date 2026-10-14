package dev.fnf.terralatitude;

import com.naturaltemperature.HumidityCalculator;
import com.naturaltemperature.HumidityContext;
import com.naturaltemperature.TemperatureCalculator;
import com.naturaltemperature.TemperatureContext;
import java.util.Objects;

/**
 * Feeds world-owned inputs to NT's existing public climate formulas. Coordinate
 * preparation mirrors the installed WorldPainter contract; mode algorithms are
 * executed from the dependency, not copied. No global Config field is assigned.
 */
public final class NaturalClimateAdapter {
    private NaturalClimateAdapter() {}

    /** Native mode output before independent outer T/H weighting; not an extra altitude pass. */
    public record Sample(double temperature, double humidity) {
        public Sample {
            if (!Double.isFinite(temperature) || !Double.isFinite(humidity)) {
                throw new IllegalArgumentException("Natural Temperature returned a nonfinite climate");
            }
        }
    }

    /** All coordinates are blocks, and raw climate inputs use the normalized -1..1 axis. */
    public static Sample sample(NtSettings settings, int blockX, int blockY, int blockZ,
                                double rawTemperature, double rawHumidity) {
        Objects.requireNonNull(settings, "NT settings");
        if (!Double.isFinite(rawTemperature) || !Double.isFinite(rawHumidity)) {
            throw new IllegalArgumentException("Raw FTF climate must be finite");
        }
        // The context-only modes need no field redirects. Other modes must never
        // silently read process-global zones if a mixin was omitted from packaging.
        if (settings.mode() >= 3 && (!NtSettingsRedirected.class.isAssignableFrom(TemperatureCalculator.class)
                || !NtSettingsRedirected.class.isAssignableFrom(HumidityCalculator.class))) {
            throw new IllegalStateException("NT per-world calculator redirects were not applied");
        }
        Coordinates coordinates = coordinates(settings, blockX, blockZ, rawTemperature, rawHumidity);
        TemperatureContext temperatureContext = temperatureContext(settings, blockX, blockY, blockZ,
                rawTemperature, coordinates);
        try (NtSnapshotScope ignored = NtSnapshotScope.enter(settings)) {
            // WorldPainter casts each native calculation to float before blending.
            float latitudeTemperature = (float) temperature(settings.mode(), temperatureContext);
            HumidityContext humidityContext = humidityContext(settings, blockX, blockY, blockZ,
                    rawTemperature, rawHumidity, latitudeTemperature, coordinates);
            float latitudeHumidity = (float) humidity(settings.mode(), humidityContext);
            return new Sample(latitudeTemperature, latitudeHumidity);
        }
    }

    static TemperatureContext temperatureContext(NtSettings settings, int x, int y, int z,
                                                   double rawTemperature, Coordinates c) {
        double a = settings.number("equatorial_distance");
        boolean partialUnderground = settings.integer("underground_randomization_type") == 1;
        return new TemperatureContext(c.adjustedZ(), c.adjustedZ(), a, settings.flag("looping_world"),
                1.0 - Math.abs(settings.number("global_temperature_mitigation_percentage") / 100.0),
                settings.number("global_temperature_modifier_percentage") / 100.0,
                rawTemperature, partialUnderground ? 0.75 : 1.0, partialUnderground ? 0.25 : 0.0,
                settings.undergroundEnabled(), settings.undergroundBelowY(), y, x, z,
                c.wrappedZ(), 0.0, (2.0 / 3.0) * a, (4.0 / 3.0) * a,
                z + settings.number("equator_offset"), a + c.displacementZ(), settings.mode(),
                c.radialDistance(), c.squareDistance(), 0.0);
    }

    static HumidityContext humidityContext(NtSettings settings, int x, int y, int z,
                                            double rawTemperature, double rawHumidity,
                                            float latitudeTemperature, Coordinates c) {
        double a = settings.number("equatorial_distance");
        int undergroundType = settings.integer("underground_randomization_type");
        boolean partialUnderground = undergroundType == 1;
        return new HumidityContext(a, rawHumidity, rawTemperature, latitudeTemperature,
                settings.flag("looping_world"), y, x, z, settings.undergroundBelowY(),
                settings.undergroundEnabled(), partialUnderground ? 0.75 : 1.0,
                partialUnderground ? 0.25 : 0.0, c.wrappedZ(), 0.0, (1.0 / 3.0) * a,
                (5.0 / 3.0) * a, 2.0 * a, settings.flag("extra_bands"),
                (2.0 / 3.0) * a, (4.0 / 3.0) * a, c.adjustedZ(), undergroundType,
                (8.0 / 3.0) * a, (10.0 / 3.0) * a,
                z + settings.number("equator_offset"), c.displacementZ() + 3.0 * a,
                c.displacementZ() - a, settings.flag("enable_humidity_bands"),
                c.adjustedX(), settings.number("humidity_bands_width"), c.adjustedZ(),
                c.radialDistance(), a + c.displacementZ(), settings.mode(), c.squareDistance());
    }

    static Coordinates coordinates(NtSettings settings, int x, int z, double rawTemperature,
                                    double rawHumidity) {
        double a = settings.number("equatorial_distance");
        double phase = ((settings.seed() & 1_048_575L) / 1_048_575.0) * 2.0 * Math.PI;
        double magnification = settings.number("wave_magnification");
        if (magnification == 0.0) magnification = 0.01; // NT's zero-wave convention.
        double noiseDisplacement = settings.noise(x, z) * 30.0;
        double waveZ = wave(x, rawTemperature, magnification, phase);
        double waveX = wave(z, rawHumidity, magnification, phase);
        double displacementZ = noiseDisplacement + waveZ;
        double adjustedZ = z + noiseDisplacement + waveZ + settings.number("equator_offset");
        // Preserve NT's addition order for the wrapped coordinate, including negatives.
        double length = 4.0 * a;
        double wrappedZ = ((z + settings.number("equator_offset") + noiseDisplacement + waveZ) % length
                + length) % length;
        double adjustedX = x + noiseDisplacement + waveX + settings.number("humidity_offset");
        return new Coordinates(adjustedZ, wrappedZ, adjustedX, displacementZ,
                Math.sqrt((double) x * x + (double) z * z), Math.max(Math.abs((double) x), Math.abs((double) z)));
    }

    private static double wave(int coordinate, double original, double magnification, double phase) {
        double frequency = (0.001 * (1.0 + original * 0.01)) / magnification;
        double amplitude = 30.0 * magnification;
        double first = amplitude * Math.sin(2.0 * Math.PI * Math.sqrt(2.1) * frequency * coordinate + 1.0 + phase);
        double second = amplitude * Math.sin(2.0 * Math.PI * Math.sqrt(28.3192026680207) * frequency * coordinate + phase);
        double third = amplitude * Math.sin(2.0 * Math.PI * Math.sqrt(3.7) * frequency * coordinate + phase);
        double fourth = amplitude * Math.sin(2.0 * Math.PI * Math.sqrt(1.9) * frequency * coordinate + phase);
        return 0.5 * (first + second + third + fourth);
    }

    record Coordinates(double adjustedZ, double wrappedZ, double adjustedX, double displacementZ,
                       double radialDistance, double squareDistance) {}

    private static double temperature(int mode, TemperatureContext context) {
        return switch (mode) {
            case 0 -> TemperatureCalculator.calculateDefault(context);
            case 1 -> TemperatureCalculator.calculateSimplified(context);
            case 2 -> TemperatureCalculator.calculateLinear(context);
            case 3, 4 -> TemperatureCalculator.calculateCircular(context);
            case 5, 6 -> TemperatureCalculator.calculateBanded(context);
            case 7, 8 -> TemperatureCalculator.calculateCircular6(context);
            case 9, 10 -> TemperatureCalculator.calculateSquare6(context);
            default -> throw new IllegalArgumentException("Unsupported NT mode " + mode);
        };
    }

    private static double humidity(int mode, HumidityContext context) {
        return switch (mode) {
            case 0 -> HumidityCalculator.calculateDefault(context);
            case 1 -> HumidityCalculator.calculateSimplified(context);
            case 2 -> HumidityCalculator.calculateLinear(context);
            case 3, 4 -> HumidityCalculator.calculateCircular(context);
            case 5, 6 -> HumidityCalculator.calculateBanded(context);
            case 7, 8 -> HumidityCalculator.calculateCircular6(context);
            case 9, 10 -> HumidityCalculator.calculateSquare6(context);
            default -> throw new IllegalArgumentException("Unsupported NT mode " + mode);
        };
    }
}
