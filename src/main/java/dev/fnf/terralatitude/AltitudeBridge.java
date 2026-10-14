package dev.fnf.terralatitude;

import com.naturaltemperature.Config;
import etcodehome.freeterraforged.concurrent.Resource;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.biome.ClimateQuerySemantics;
import etcodehome.freeterraforged.world.worldgen.biome.FTFClimateSampler;
import etcodehome.freeterraforged.world.worldgen.biome.UndergroundBiomeSurfaceProtection;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.runtime.WorldgenPlan;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;

/** Adjust only the final sampling stage of an FTF-owned Overworld plan. */
public final class AltitudeBridge {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final AtomicBoolean REPORTED_ACTIVE = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    private AltitudeBridge() {}

    public static Climate.TargetPoint adjust(Climate.Sampler sampler, int quartX, int quartY, int quartZ,
                                            Climate.TargetPoint original) {
        AltitudeConfig.Snapshot options = AltitudeConfig.current();
        double influence = Config.natural_temperature_influence / 100.0;
        if (!options.enabled() || original == null || !Double.isFinite(influence) || influence <= 0.0
                || original.temperature() <= -10000L || options.settings().lapseRateCPerKm() == 0.0) {
            return original;
        }
        if (!((Object) sampler instanceof FTFClimateSampler ftf)) return original;
        WorldgenPlan plan = ftf.getWorldgenPlan();
        if (plan == null || plan.owner() == null || plan.owner().dimension() == null
                || !OVERWORLD.equals(plan.owner().dimension().location())) return original;
        ClimateQuerySemantics semantics = ftf.climateQuerySemantics();
        if (semantics == null || semantics.preset() == null) return original;
        GeneratorContext context = semantics.surfaceContext();
        if (context == null || context.preset == null || context.lookup == null || context.levels == null) return original;
        if (options.preserveRandomizedUnderground() && Config.randomize_underground
                && QuartPos.toBlock(quartY) <= Config.randomize_underground_below_y) return original;

        // Use FTF's pooled cell and existing terrain lookup. No chunk loading,
        // heightmap queries, biome recursion, or cache retaining closed worlds.
        try (Resource<Cell> resource = Cell.getResource()) {
            int surfaceY = UndergroundBiomeSurfaceProtection.sampleSurfaceY(
                    context, resource.get(), QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ));
            long temperature = AltitudeModel.adjustTemperature(original.temperature(), surfaceY,
                    context.levels.waterLevel, influence, options.settings());
            if (REPORTED_ACTIVE.compareAndSet(false, true)) {
                TerraLatitude.LOGGER.info("Altitude correction reached FTF Overworld biome sampling; preset sea level={}, NT influence={}%. Latitude and humidity are retained.",
                        context.levels.waterLevel, Config.natural_temperature_influence);
            }
            if (temperature == original.temperature()) return original;
            return new Climate.TargetPoint(temperature, original.humidity(), original.continentalness(),
                    original.erosion(), original.depth(), original.weirdness());
        } catch (RuntimeException exception) {
            // A failed optional correction must not destroy a worldgen task.
            // Log once rather than flooding one error per biome sample.
            if (REPORTED_FAILURE.compareAndSet(false, true)) {
                TerraLatitude.LOGGER.error("Altitude terrain lookup failed; returning the original climate for affected samples. Check the installed FTF build.", exception);
            }
            return original;
        }
    }
}
