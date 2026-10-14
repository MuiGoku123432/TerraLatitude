package dev.fnf.terralatitude;

import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.biome.FTFClimateSampler;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Owns world access and passes immutable inputs to the climate composition. */
public final class IntegratedClimate {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private IntegratedClimate() {}

    private record Binding(GeneratorContext context, ClimateProfile profile) {}
    public record SurfaceEvaluation(int sampleY, RawClimateAccess.Sample regional,
                                    ClimateComposition.Evaluation climate) {}

    /** Null means this sampler is outside the owned integration; other mods continue normally. */
    public static ClimateComposition.Evaluation evaluate(Climate.Sampler sampler, int quartX, int quartY, int quartZ) {
        Binding binding = owned(sampler);
        if (binding == null) return null;
        int x = QuartPos.toBlock(quartX), y = QuartPos.toBlock(quartY), z = QuartPos.toBlock(quartZ);
        Climate.TargetPoint base = nativeSample(sampler, x, y, z);
        RawClimateAccess.Sample raw = ClimateComposition.needsRegional(binding.profile(), y)
                ? RawClimateAccess.sample(binding.context(), x, z) : null;
        return ClimateComposition.compose(binding.profile(), x, y, z, base, raw);
    }

    /** Surface diagnostics reuse one raw acquisition for height and expected climate. */
    public static SurfaceEvaluation evaluateSurface(Climate.Sampler sampler, int quartX, int quartZ) {
        Binding binding = owned(sampler);
        if (binding == null) return null;
        int x = QuartPos.toBlock(quartX), z = QuartPos.toBlock(quartZ);
        RawClimateAccess.Sample raw = RawClimateAccess.sample(binding.context(), x, z);
        if (!raw.valid()) throw new IllegalStateException("FTF raw climate is unavailable.");
        int y = DiagnosticMath.surfaceSampleY(raw.surfaceY(), raw.seaLevel());
        Climate.TargetPoint base = nativeSample(sampler, x, y, z);
        return new SurfaceEvaluation(y, raw,
                ClimateComposition.compose(binding.profile(), x, y, z, base, raw));
    }

    private static Binding owned(Climate.Sampler sampler) {
        if (!((Object) sampler instanceof FTFClimateSampler ftf)) return null;
        var plan = ftf.getWorldgenPlan();
        var semantics = ftf.climateQuerySemantics();
        if (plan == null || plan.owner() == null || plan.owner().dimension() == null
                || !OVERWORLD.equals(plan.owner().dimension().location()) || semantics == null
                || semantics.surfaceContext() == null) return null;
        ClimateProfile profile = WorldProfiles.forSampler(ftf);
        if (profile == null || !profile.enabled()) return null;
        return new Binding(semantics.surfaceContext(), profile);
    }
    private static Climate.TargetPoint nativeSample(Climate.Sampler s, int x, int y, int z) {
        var point = new DensityFunction.SinglePointContext(x, y, z);
        return Climate.target((float) s.temperature().compute(point), (float) s.humidity().compute(point),
                (float) s.continentalness().compute(point), (float) s.erosion().compute(point),
                (float) s.depth().compute(point), (float) s.weirdness().compute(point));
    }
}
