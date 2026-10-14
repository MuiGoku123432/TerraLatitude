package dev.fnf.terralatitude;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import etcodehome.freeterraforged.world.worldgen.biome.FTFClimateSampler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only inspection; evaluates noise/biomes without requesting or loading chunks. */
public final class ClimateCommands {
    private ClimateCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("terralatitude").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("sample").executes(c -> sample(c.getSource())))
                .then(Commands.literal("transect")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 8192))
                                .then(Commands.argument("step", IntegerArgumentType.integer(16, 2048))
                                        .executes(c -> transect(c.getSource(), IntegerArgumentType.getInteger(c, "radius"),
                                                IntegerArgumentType.getInteger(c, "step"), DiagnosticMath.Axis.NORTH_SOUTH))
                                        .then(Commands.literal("east_west")
                                                .executes(c -> transect(c.getSource(), IntegerArgumentType.getInteger(c, "radius"),
                                                        IntegerArgumentType.getInteger(c, "step"), DiagnosticMath.Axis.EAST_WEST)))))));
    }
    private static int status(CommandSourceStack source) {
        try {
            var row = inspect(source, (int) Math.floor(source.getPosition().x), (int) Math.floor(source.getPosition().z));
            var p = row.profile();
            source.sendSuccess(() -> Component.literal("TerraLatitude profile loaded " + p.fingerprint().substring(0, 12)
                    + "; NT mode=" + p.naturalTemperature().mode() + "; temperature=" + p.temperatureStrength()
                    + "; moisture=" + p.moistureStrength() + "; transition=" + p.transitionRadius()
                    + " blocks; model=" + ClimateComposition.MODEL + "; sampler check " + row.samplerCheck()
                    + " at current surface (temperature/moisture)."), false);
            return 1;
        } catch (RuntimeException e) { source.sendFailure(Component.literal("TerraLatitude diagnostic failed: " + e.getMessage())); return 0; }
    }
    private static int sample(CommandSourceStack source) {
        try {
            var row = inspect(source, (int) Math.floor(source.getPosition().x), (int) Math.floor(source.getPosition().z));
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "TerraLatitude (%d, %d, %d): T %.4f / %.4f expected, moisture %.4f / %.4f expected; %s; ground %d; biome %s. Climate units, not Celsius.",
                    row.x(), row.y(), row.z(), row.temperature(), row.integratedT(), row.humidity(), row.integratedH(),
                    row.samplerCheck(), row.ground(), row.biome())), false);
            return 1;
        } catch (RuntimeException e) { source.sendFailure(Component.literal("TerraLatitude diagnostic failed: " + e.getMessage())); return 0; }
    }
    private static int transect(CommandSourceStack source, int radius, int step, DiagnosticMath.Axis axis) {
        int count = DiagnosticMath.transectCount(radius, step);
        if (count > 129) { source.sendFailure(Component.literal("Use a larger step: maximum 129 samples per transect.")); return 0; }
        try {
            int x = (int) Math.floor(source.getPosition().x), z = (int) Math.floor(source.getPosition().z);
            StringBuilder csv = new StringBuilder("x,y,z,ground,temperature,humidity,raw_temperature,raw_humidity,band_temperature,band_humidity,biome,integrated_temperature,integrated_humidity,sea_level_temperature,altitude_delta,temperature_delta,humidity_delta,normalized_elevation,altitude_strength,climate_model,profile_fingerprint\n");
            for (int offset = -radius; offset <= radius; offset += step) {
                var position = DiagnosticMath.transectPosition(x, z, offset, axis);
                var row = inspect(source, position.x(), position.z());
                csv.append(row.csv()).append('\n');
            }
            Path dir = WorldProfiles.root(source.getServer()).resolve("terralatitude-diagnostics");
            Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, "transect-" + Instant.now().toEpochMilli() + "-", ".csv");
            Files.writeString(file, csv.toString());
            source.sendSuccess(() -> Component.literal("Exported " + count + " " + axis.label() + " surface samples to " + file
                    + ". No chunks requested; ungenerated biome predictions may still be altered by later pack hooks."), false);
            return count;
        } catch (Exception e) { source.sendFailure(Component.literal("TerraLatitude diagnostic failed: " + e.getMessage())); return 0; }
    }
    private static Row inspect(CommandSourceStack source, int x, int z) {
        var chunkSource = source.getLevel().getChunkSource();
        var sampler = chunkSource.randomState().sampler();
        if (!((Object) sampler instanceof FTFClimateSampler ftf) || ftf.climateQuerySemantics() == null)
            throw new IllegalStateException("Current dimension does not have an FTF climate sampler.");
        // Align both diagnostics and biome selection with Minecraft's quart sampling grid.
        int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
        x = QuartPos.toBlock(qx); z = QuartPos.toBlock(qz);
        var surface = IntegratedClimate.evaluateSurface(sampler, qx, qz);
        if (surface == null) throw new IllegalStateException("TerraLatitude is inactive for this sampler/world.");
        var raw = surface.regional();
        int y = surface.sampleY(), qy = QuartPos.fromBlock(y);
        var evaluation = surface.climate();
        var actual = ftf.getWorldgenPlan().samplerDecoration().sample(sampler, qx, qy, qz);
        var expected = evaluation.point();
        var biome = chunkSource.getGenerator().getBiomeSource().getNoiseBiome(qx, qy, qz, sampler);
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse("unregistered");
        double integratedT = expected.temperature() / 10000.0, integratedH = expected.humidity() / 10000.0;
        // This is the net elevation correction including final climate quantization.
        // Pass-through temperature and randomized caves have no integrated surface correction.
        double altitudeDelta = evaluation.regional() != null && evaluation.profile().temperatureStrength() > 0
                && evaluation.profile().naturalTemperature().influence() > 0
                ? integratedT - evaluation.seaLevelTemperature() : Double.NaN;
        return new Row(x, y, z, raw.surfaceY(), actual.temperature() / 10000.0, actual.humidity() / 10000.0,
                raw.temperature(), raw.humidity(), evaluation.bandTemperature(), evaluation.bandHumidity(), id,
                integratedT, integratedH, evaluation.seaLevelTemperature(), altitudeDelta,
                (actual.temperature() - expected.temperature()) / 10000.0,
                (actual.humidity() - expected.humidity()) / 10000.0,
                actual.temperature() == expected.temperature() && actual.humidity() == expected.humidity(),
                raw.normalizedInlandElevation(), evaluation.profile());
    }
    private record Row(int x, int y, int z, int ground, double temperature, double humidity,
                       double rawT, double rawH, double bandT, double bandH, String biome,
                       double integratedT, double integratedH, double seaLevelT, double altitudeDelta,
                       double temperatureDelta, double humidityDelta, boolean samplerMatches,
                       float normalizedElevation, ClimateProfile profile) {
        String samplerCheck() { return samplerMatches ? "MATCH" : "MISMATCH"; }
        String csv() { return String.format(Locale.ROOT, "%d,%d,%d,%d,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%s,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.9f,%.9f,%s,%s",
                x, y, z, ground, temperature, humidity, rawT, rawH, bandT, bandH, biome,
                integratedT, integratedH, seaLevelT, altitudeDelta, temperatureDelta, humidityDelta,
                normalizedElevation, profile.altitudeStrength(), ClimateComposition.MODEL, profile.fingerprint()); }
    }
}
