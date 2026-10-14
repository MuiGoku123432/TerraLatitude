package dev.fnf.terralatitude;

import dev.fnf.terralatitude.mixin.ClimateModuleAccessor;
import etcodehome.freeterraforged.concurrent.Resource;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.biome.UndergroundBiomeSurfaceProtection;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.climate.Climate;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.terrain.TerrainType;

/**
 * Obtains regional noise before elevation shaping without undoing quantized
 * climate or modifying FTF's shared cells. The caller must establish ownership.
 */
public final class RawClimateAccess {
    private RawClimateAccess() {}

    public record Sample(boolean valid, double temperature, double humidity,
                         double nativeTemperature, double nativeHumidity,
                         int surfaceY, int seaLevel, float cellHeight,
                         float water, float ground, float normalizedInlandElevation,
                         int worldHeight, boolean aboveGround) {
        public static Sample unavailable() {
            return new Sample(false, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    0, 0, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0, false);
        }
    }

    public static Sample sample(GeneratorContext context, int blockX, int blockZ) {
        if (context == null || context.lookup == null || context.levels == null
                || context.lookup.getHeightmap() == null) return Sample.unavailable();
        Climate climate = context.lookup.getHeightmap().climate();
        if (climate == null || !((Object) climate.biomeNoise() instanceof ClimateModuleAccessor noise)) {
            return Sample.unavailable();
        }
        Levels levels = context.levels;
        try (Resource<Cell> resource = Cell.getResource()) {
            Cell cell = resource.get();
            // One cached-or-calculated terrain lookup. A false lookup return means
            // the uncached path was used; it is not an unavailable sample.
            int surfaceY = UndergroundBiomeSurfaceProtection.sampleSurfaceY(context, cell, blockX, blockZ);
            float x = blockX;
            float z = blockZ;

            // Climate.apply uses its original region edge to shift both regional
            // noise coordinates. Cached cells retain that edge after applyEdge.
            if (cell.height > climate.levels().water
                    && (cell.biomeRegionEdge < 0.4F || cell.terrain == TerrainType.MOUNTAIN_CHAIN)) {
                float distance = RawClimateMath.edgeDistance(cell.biomeRegionEdge, climate.offsetDistance());
                x += climate.offsetX().compute(blockX, blockZ, 0) * distance;
                z += climate.offsetZ().compute(blockX, blockZ, 0) * distance;
            }
            RawClimateMath.Coordinates coordinates = RawClimateMath.moduleCoordinates(x, z,
                    noise.terralatitude$warpX().compute(x, z, 0),
                    noise.terralatitude$warpZ().compute(x, z, 0),
                    noise.terralatitude$warpStrength(), noise.terralatitude$biomeFrequency());
            float temperature = RawClimateMath.normalize(
                    noise.terralatitude$temperature().compute(coordinates.x(), coordinates.z(), 0));
            float humidity = RawClimateMath.normalize(
                    noise.terralatitude$moisture().compute(coordinates.x(), coordinates.z(), 0));
            float normalizedElevation = Math.max(0.0F,
                    Math.min(1.0F, levels.getNormalizedInlandElevation(cell.height)));
            boolean valid = Float.isFinite(temperature) && Float.isFinite(humidity)
                    && Float.isFinite(cell.temperature) && Float.isFinite(cell.moisture)
                    && Float.isFinite(cell.height) && Float.isFinite(normalizedElevation)
                    && Float.isFinite(levels.water) && Float.isFinite(levels.ground)
                    && levels.worldHeight > levels.waterLevel;
            if (!valid) return Sample.unavailable();
            return new Sample(true, temperature, humidity, cell.temperature, cell.moisture,
                    surfaceY, levels.waterLevel, cell.height, levels.water, levels.ground,
                    normalizedElevation, levels.worldHeight, cell.height > levels.ground);
        }
    }
}
