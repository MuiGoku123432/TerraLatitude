package dev.fnf.terralatitude;

import etcodehome.freeterraforged.world.worldgen.biome.FTFClimateSampler;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Single annual climate composition before FTF's owned provider/decorator selection. */
public final class IntegratedClimate {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private IntegratedClimate() {}
    public record Evaluation(Climate.TargetPoint point, Climate.TargetPoint nativePoint,
                             RawClimateAccess.Sample regional, double bandTemperature, double bandHumidity,
                             double seaLevelTemperature, boolean randomizedUnderground, ClimateProfile profile) {}

    /** Null means this sampler is outside the owned integration; other mods continue normally. */
    public static Evaluation evaluate(Climate.Sampler sampler, int quartX, int quartY, int quartZ) {
        if (!((Object) sampler instanceof FTFClimateSampler ftf)) return null;
        var plan = ftf.getWorldgenPlan();
        var semantics = ftf.climateQuerySemantics();
        if (plan == null || plan.owner() == null || plan.owner().dimension() == null
                || !OVERWORLD.equals(plan.owner().dimension().location()) || semantics == null
                || semantics.surfaceContext() == null) return null;
        ClimateProfile profile = WorldProfiles.forSampler(ftf);
        if (profile == null || !profile.enabled()) return null;
        int x = QuartPos.toBlock(quartX), y = QuartPos.toBlock(quartY), z = QuartPos.toBlock(quartZ);
        Climate.TargetPoint base = nativeSample(sampler, x, y, z);
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
        RawClimateAccess.Sample raw = RawClimateAccess.sample(semantics.surfaceContext(), x, z);
        if (!raw.valid()) throw new IllegalStateException("TerraLatitude cannot read FTF raw climate for its saved world profile");
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
    private static Climate.TargetPoint nativeSample(Climate.Sampler s, int x, int y, int z) {
        var point = new DensityFunction.SinglePointContext(x, y, z);
        return Climate.target((float) s.temperature().compute(point), (float) s.humidity().compute(point),
                (float) s.continentalness().compute(point), (float) s.erosion().compute(point),
                (float) s.depth().compute(point), (float) s.weirdness().compute(point));
    }
}
