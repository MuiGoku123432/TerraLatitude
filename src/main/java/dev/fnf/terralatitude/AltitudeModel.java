package dev.fnf.terralatitude;

import java.util.Objects;

/**
 * Pure altitude correction for the temperature component of a quantized climate sample.
 *
 * <p>The environmental lapse rate is inspired by atmospheric cooling with elevation.
 * Effective meters per block and degrees per climate unit are game-scaling choices:
 * Minecraft's normalized biome climate is not a Celsius thermometer. Elevation must
 * describe the predicted terrain surface, rather than the Y coordinate of a biome
 * query. This model does not change humidity, weather, snow layers, or player warmth.
 *
 * <p>The additional cooling is weighted by Natural Temperature's influence. This
     * approximates the missing altitude effect without adding a full second
     * correction to the FreeTerraForged contribution at partial influence. Some
     * Natural Temperature modes retain original temperature internally as well;
     * this is not an exact decomposition of FreeTerraForged's altitude shaping.
 */
public final class AltitudeModel {
    private static final long CLIMATE_LIMIT = 10_000L;
    private static final double QUANTIZATION = 10_000.0;

    private AltitudeModel() {
    }

    /** Configurable physical inspiration and explicit game-world scaling. */
    public record Settings(double lapseRateCPerKm,
                           double effectiveMetersPerBlock,
                           double degreesPerClimateUnit) {
        public Settings {
            requireRange("lapseRateCPerKm", lapseRateCPerKm, 0.0, 15.0);
            requireRange("effectiveMetersPerBlock", effectiveMetersPerBlock, 0.1, 100.0);
            requireRange("degreesPerClimateUnit", degreesPerClimateUnit, 1.0, 100.0);
        }

        public static Settings defaults() {
            return new Settings(6.5, 10.0, 20.0);
        }

        private static void requireRange(String name, double value, double min, double max) {
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new IllegalArgumentException(name + " must be finite and in ["
                        + min + ", " + max + "]");
            }
        }
    }

    public static Settings defaults() {
        return Settings.defaults();
    }

    /**
     * Computes nonnegative, unquantized cooling in normalized climate units.
     * Influence uses the fraction 0..1, not a percentage. Negative or nonfinite
     * influence and nonfinite heights fail open with zero cooling; finite influence
     * above one is capped. Terrain at or below sea level receives no correction.
     */
    public static double coolingClimateUnits(double surfaceY, double seaLevel,
                                             double influence, Settings settings) {
        Objects.requireNonNull(settings, "settings");
        if (!Double.isFinite(surfaceY) || !Double.isFinite(seaLevel)
                || !Double.isFinite(influence) || influence <= 0.0
                || surfaceY <= seaLevel || settings.lapseRateCPerKm() == 0.0) {
            return 0.0;
        }

        double coolingPerBlock = settings.lapseRateCPerKm()
                * settings.effectiveMetersPerBlock() / 1000.0
                / settings.degreesPerClimateUnit() * Math.min(influence, 1.0);
        if (coolingPerBlock == 0.0) {
            return 0.0;
        }
        double elevation = surfaceY - seaLevel;
        // A finite pair of extreme doubles can overflow when subtracted. Scaling
        // each first can still preserve a finite, useful result in that case.
        double cooling = Double.isFinite(elevation)
                ? elevation * coolingPerBlock
                : surfaceY * coolingPerBlock - seaLevel * coolingPerBlock;
        return Double.isFinite(cooling) ? cooling : Double.MAX_VALUE;
    }

    /**
     * Applies cooling to a climate value quantized in ten-thousandths. Cooling is
     * rounded once; a sub-quantum or disabled correction preserves the original
     * long exactly, including values outside the usual climate range. Actual
     * corrections saturate to the normalized interval [-10000, 10000].
     */
    public static long adjustTemperature(long original, double surfaceY, double seaLevel,
                                          double influence, Settings settings) {
        long cooling = Math.round(coolingClimateUnits(surfaceY, seaLevel, influence, settings)
                * QUANTIZATION);
        if (cooling == 0L) {
            return original;
        }
        long adjusted = original < Long.MIN_VALUE + cooling
                ? Long.MIN_VALUE : original - cooling;
        return Math.max(-CLIMATE_LIMIT, Math.min(CLIMATE_LIMIT, adjusted));
    }
}
