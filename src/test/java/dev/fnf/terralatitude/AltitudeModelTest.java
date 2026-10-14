package dev.fnf.terralatitude;

/** Standalone tests: run with Java 21; no game, world, or test library is required. */
public final class AltitudeModelTest {
    private static final AltitudeModel.Settings DEFAULTS = AltitudeModel.defaults();
    private static int assertions;

    private AltitudeModelTest() {
    }

    public static void main(String[] args) {
        defaultExamples();
        noEffectPreservesOriginal();
        influenceAndScaling();
        quantization();
        invalidInputsAndOverflow();
        settingsValidation();
        generatedInvariants();
        System.out.println("AltitudeModelTest passed (" + assertions + " assertions)");
    }

    private static void defaultExamples() {
        equal(new AltitudeModel.Settings(6.5, 10.0, 20.0), DEFAULTS, "default settings");
        equal(DEFAULTS, AltitudeModel.Settings.defaults(), "both defaults APIs agree");
        close(0.325, cooling(162, 62, 1), "100 blocks cooling");
        close(0.65, cooling(262, 62, 1), "200 blocks cooling");
        close(1.3, cooling(462, 62, 1), "400 blocks cooling");
        equal(8_000, adjust(8_000, 62, 62, 1), "tropical coast");
        equal(4_750, adjust(8_000, 162, 62, 1), "tropical foothills");
        equal(1_500, adjust(8_000, 262, 62, 1), "tropical mountain");
        equal(-5_000, adjust(8_000, 462, 62, 1), "tropical summit");
        equal(-6_500, adjust(0, 262, 62, 1), "temperate mountain");
        equal(-10_000, adjust(-8_000, 162, 62, 1), "cold saturation");
    }

    private static void noEffectPreservesOriginal() {
        long[] originals = {Long.MIN_VALUE, -20_000, -10_000, 0, 8_000, 10_000,
                20_000, Long.MAX_VALUE};
        for (long original : originals) {
            equal(original, adjust(original, 62, 62, 1), "sea-level identity");
            equal(original, adjust(original, -128, 62, 1), "below-sea identity");
            equal(original, adjust(original, 500, 62, 0), "zero influence identity");
            equal(original, AltitudeModel.adjustTemperature(original, 500, 62, 1,
                    new AltitudeModel.Settings(0, 10, 20)), "disabled lapse identity");
            equal(original, adjust(original, 62.001, 62, 1), "sub-quantum identity");
        }
    }

    private static void influenceAndScaling() {
        equal(4_750, adjust(8_000, 262, 62, 0.5), "half NT contribution");
        equal(adjust(8_000, 262, 62, 1), adjust(8_000, 262, 62, 2), "influence cap");
        close(cooling(262, 62, 1), cooling(300, 100, 1), "sea-level translation");
        close(cooling(0, -200, 1), cooling(262, 62, 1), "negative world coordinates");
        close(0.65, AltitudeModel.coolingClimateUnits(162, 62, 1,
                new AltitudeModel.Settings(6.5, 20, 20)), "height scale doubles cooling");
        close(0.1625, AltitudeModel.coolingClimateUnits(162, 62, 1,
                new AltitudeModel.Settings(6.5, 10, 40)), "climate scale halves cooling");
    }

    private static void quantization() {
        // These settings give exactly 0.001 climate units per block, so 0.05
        // blocks is the half-quantum boundary. Keep the reference sea level zero.
        AltitudeModel.Settings settings = new AltitudeModel.Settings(10, 1, 10);
        equal(5_000, AltitudeModel.adjustTemperature(5_000, 0.049, 0, 1, settings),
                "less than half quantum");
        equal(4_999, AltitudeModel.adjustTemperature(5_000, 0.05, 0, 1, settings),
                "half quantum rounds up");
        equal(4_999, AltitudeModel.adjustTemperature(5_000, 0.149, 0, 1, settings),
                "cooling rounded only once");
        equal(4_998, AltitudeModel.adjustTemperature(5_000, 0.151, 0, 1, settings),
                "above next half quantum rounds up");
        equal(-1, AltitudeModel.adjustTemperature(0, 0.05, 0, 1, settings),
                "round positive cooling before subtraction");
    }

    private static void invalidInputsAndOverflow() {
        double[] nonfinite = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double value : nonfinite) {
            equal(20_000, adjust(20_000, value, 62, 1), "invalid surface fails open");
            equal(20_000, adjust(20_000, 262, value, 1), "invalid sea level fails open");
            equal(20_000, adjust(20_000, 262, 62, value), "invalid influence fails open");
            close(0, cooling(value, 62, 1), "invalid surface has zero cooling");
        }
        equal(20_000, adjust(20_000, 262, 62, -0.1), "negative influence fails open");
        equal(-10_000, adjust(Long.MIN_VALUE, 262, 62, 1), "subtraction cannot wrap");
        equal(-10_000, adjust(Long.MIN_VALUE + 1, 262, 62, 1), "near-underflow saturates");
        equal(10_000, adjust(Long.MAX_VALUE, 262, 62, 1), "large positive value clamps");
        equal(-10_000, adjust(10_000, Double.MAX_VALUE, -Double.MAX_VALUE, 1),
                "extreme elevation cools without long overflow");
        check(Double.isFinite(cooling(Double.MAX_VALUE, -Double.MAX_VALUE, 1)),
                "extreme heights yield finite cooling");
        AltitudeModel.Settings steep = new AltitudeModel.Settings(15, 100, 1);
        check(Double.isFinite(AltitudeModel.coolingClimateUnits(Double.MAX_VALUE,
                -Double.MAX_VALUE, 1, steep)), "overflowing cooling saturates finitely");
    }

    private static void settingsValidation() {
        // All endpoints are legal, with zero lapse serving as an explicit disable.
        new AltitudeModel.Settings(0, 0.1, 1);
        new AltitudeModel.Settings(15, 100, 100);
        assertions += 2;
        double[] invalidLapse = {-0.1, 15.1, Double.NaN, Double.POSITIVE_INFINITY};
        double[] invalidMeters = {0, 0.09, 100.1, Double.NaN, Double.NEGATIVE_INFINITY};
        double[] invalidDegrees = {0, 0.9, 100.1, Double.NaN, Double.POSITIVE_INFINITY};
        for (double value : invalidLapse) {
            rejects(() -> new AltitudeModel.Settings(value, 10, 20), "invalid lapse");
        }
        for (double value : invalidMeters) {
            rejects(() -> new AltitudeModel.Settings(6.5, value, 20), "invalid meters");
        }
        for (double value : invalidDegrees) {
            rejects(() -> new AltitudeModel.Settings(6.5, 10, value), "invalid degrees");
        }
    }

    private static void generatedInvariants() {
        for (double influence : new double[]{0.1, 0.5, 1.0}) {
            for (long original = -10_000; original <= 10_000; original += 500) {
                long previous = original;
                for (int ground = 62; ground <= 1_024; ground++) {
                    long corrected = adjust(original, ground, 62, influence);
                    check(corrected <= previous, "monotone elevation cooling");
                    check(corrected >= -10_000 && corrected <= 10_000, "normalized output");
                    check(corrected <= original, "correction never warms");
                    equal(corrected, adjust(original, ground + 100, 162, influence),
                            "translated elevation sweep");
                    previous = corrected;
                }
            }
            for (int ground = 62; ground <= 528; ground += 7) {
                long previous = -10_000;
                for (long latitude = -10_000; latitude <= 10_000; latitude += 100) {
                    long corrected = adjust(latitude, ground, 62, influence);
                    check(corrected >= previous, "warmer latitude stays warmer or ties at clamp");
                    previous = corrected;
                }
            }
        }
        // This API deliberately has no query-Y or humidity argument: the adapter
        // must supply a surface estimate and preserve the other climate components.
    }

    private static double cooling(double surface, double sea, double influence) {
        return AltitudeModel.coolingClimateUnits(surface, sea, influence, DEFAULTS);
    }

    private static long adjust(long original, double surface, double sea, double influence) {
        return AltitudeModel.adjustTemperature(original, surface, sea, influence, DEFAULTS);
    }

    private static void close(double expected, double actual, String label) {
        check(Math.abs(expected - actual) <= 1.0e-12, label + ": expected " + expected
                + ", got " + actual);
    }

    private static void equal(long expected, long actual, String label) {
        check(expected == actual, label + ": expected " + expected + ", got " + actual);
    }

    private static void equal(Object expected, Object actual, String label) {
        check(expected.equals(actual), label + ": expected " + expected + ", got " + actual);
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
