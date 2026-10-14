package dev.fnf.terralatitude;

import java.util.Objects;
import net.minecraft.world.level.biome.Climate;

/** Deterministic composition of supplied inputs. No world lookup or mutable FTF/NT settings. */
public final class ClimateComposition {
    /** Schema-1 profiles retain this arithmetic; this identifier does not alter their JSON. */
    public static final String MODEL = "ftf-nt-legacy-0.2.1";
    private ClimateComposition() {}
    public record Evaluation(Climate.TargetPoint point, Climate.TargetPoint nativePoint,
                             RawClimateAccess.Sample regional, double bandTemperature, double bandHumidity,
                             double seaLevelTemperature, boolean randomizedUnderground, ClimateProfile profile) {}

    static boolean needsRegional(ClimateProfile profile, int blockY) {
        NtSettings nt = profile.naturalTemperature();
        return nt.influence() != 0.0
                && (profile.temperatureStrength() != 0.0 || profile.moistureStrength() != 0.0)
                && !(nt.undergroundEnabled() && blockY <= nt.undergroundBelowY());
    }

    /** Coordinates are quart-aligned blocks. Raw inputs must belong to this position and owner. */
    public static Evaluation compose(ClimateProfile profile, int x, int y, int z,
                                     Climate.TargetPoint base, RawClimateAccess.Sample raw) {
        Objects.requireNonNull(profile, "World climate profile");
        Objects.requireNonNull(base, "Native climate target");
        if (!profile.enabled()) throw new IllegalArgumentException("Disabled profiles must use the upstream sampler path");
        NtSettings nt = profile.naturalTemperature();
        double influence = nt.influence();
        double tw = influence * profile.temperatureStrength(), hw = influence * profile.moistureStrength();
        if (influence == 0.0 || tw == 0.0 && hw == 0.0) {
            return new Evaluation(base, base, null, Double.NaN, Double.NaN, Double.NaN, false, profile);
        }
        if (nt.undergroundEnabled() && y <= nt.undergroundBelowY()) {
            // Preserve NT's explicit randomized-cave model and its original common influence.
            var caves = NaturalClimateAdapter.sample(nt, x, y, z, base.temperature() / 10000.0, base.humidity() / 10000.0);
            double t = ClimateMath.blend(base.temperature() / 10000.0, caves.temperature(), influence);
            double h = ClimateMath.blend(base.humidity() / 10000.0, caves.humidity(), influence);
            return new Evaluation(replace(base, t, h, true, true), base, null,
                    caves.temperature(), caves.humidity(), t, true, profile);
        }
        if (raw == null || !raw.valid()) throw new IllegalStateException("TerraLatitude cannot read FTF raw climate for its saved world profile");
        var center = NaturalClimateAdapter.sample(nt, x, y, z, raw.temperature(), raw.humidity());
        double bandT = center.temperature(), bandH = center.humidity();
        int radius = profile.transitionRadius();
        if (radius > 0) {
            var west = NaturalClimateAdapter.sample(nt, Math.subtractExact(x, radius), y, z, raw.temperature(), raw.humidity());
            var east = NaturalClimateAdapter.sample(nt, Math.addExact(x, radius), y, z, raw.temperature(), raw.humidity());
            var north = NaturalClimateAdapter.sample(nt, x, y, Math.subtractExact(z, radius), raw.temperature(), raw.humidity());
            var south = NaturalClimateAdapter.sample(nt, x, y, Math.addExact(z, radius), raw.temperature(), raw.humidity());
            bandT = ClimateMath.smooth(bandT, west.temperature(), east.temperature(), north.temperature(), south.temperature());
            bandH = ClimateMath.smooth(bandH, west.humidity(), east.humidity(), north.humidity(), south.humidity());
        }
        double sea = ClimateMath.blend(raw.temperature(), bandT, tw);
        double t = ClimateMath.altitude(sea, raw.aboveGround(), raw.normalizedInlandElevation(), profile.altitudeStrength());
        // Native moisture includes FTF's existing terrain shaping. It is not applied again.
        double h = ClimateMath.blend(base.humidity() / 10000.0, bandH, hw);
        if (!Double.isFinite(t) || !Double.isFinite(h)) throw new IllegalStateException("TerraLatitude produced a nonfinite climate sample");
        return new Evaluation(replace(base, t, h, tw > 0, hw > 0), base, raw, bandT, bandH, sea, false, profile);
    }
    private static Climate.TargetPoint replace(Climate.TargetPoint original, double t, double h, boolean changeT, boolean changeH) {
        return new Climate.TargetPoint(changeT ? Climate.quantizeCoord((float) ClimateMath.clamp(t)) : original.temperature(),
                changeH ? Climate.quantizeCoord((float) ClimateMath.clamp(h)) : original.humidity(),
                original.continentalness(), original.erosion(), original.depth(), original.weirdness());
    }
}
