package dev.fnf.terralatitude;

/** Stable annual climate composition; no seasons or query-height lapse correction. */
public final class ClimateMath {
    private ClimateMath() {}
    public static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
    public static double blend(double regional, double band, double strength) {
        return regional + strength * (band - regional);
    }
    /**
     * Retain schema-1 double rounding locally. FTF's public float helper rounds its
     * exponent/results differently and must not silently change existing worlds.
     * Elevation and the ground gate still come from FTF; strength is world-saved.
     */
    public static double altitude(double seaLevelClimate, boolean aboveGround,
                                  double normalizedElevation, double strength) {
        if (!aboveGround) return clamp(seaLevelClimate);
        double zeroToOne = (clamp(seaLevelClimate) + 1.0) * 0.5;
        return 2.0 * Math.pow(zeroToOne, Math.pow(2.0, normalizedElevation * strength)) - 1.0;
    }
    /** Convex five-tap kernel: no extra noise, overshoot, or smoothing of terrain itself. */
    public static double smooth(double center, double west, double east, double north, double south) {
        return center * 0.5 + (west + east + north + south) * 0.125;
    }
}
