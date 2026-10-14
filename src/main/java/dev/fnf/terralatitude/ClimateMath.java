package dev.fnf.terralatitude;

import etcodehome.freeterraforged.world.worldgen.cell.climate.TemperatureShaping;

/** Stable annual climate composition; no seasons or query-height lapse correction. */
public final class ClimateMath {
    private ClimateMath() {}
    public static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
    public static double blend(double regional, double band, double strength) {
        return regional + strength * (band - regional);
    }
    /** Schema-1 compatibility arithmetic for FTF's curve; explicit saved strength, no global preset read. */
    public static double altitude(double seaLevelClimate, boolean aboveGround,
                                  double normalizedElevation, double strength) {
        return TemperatureShaping.applyNormalized(seaLevelClimate, aboveGround, normalizedElevation, strength);
    }
    /** Convex five-tap kernel: no extra noise, overshoot, or smoothing of terrain itself. */
    public static double smooth(double center, double west, double east, double north, double south) {
        return center * 0.5 + (west + east + north + south) * 0.125;
    }
}
