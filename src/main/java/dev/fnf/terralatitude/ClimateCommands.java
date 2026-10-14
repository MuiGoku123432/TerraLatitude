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
                                                IntegerArgumentType.getInteger(c, "step")))))));
    }
    private static int status(CommandSourceStack source) {
        var sampler = source.getLevel().getChunkSource().randomState().sampler();
        var result = IntegratedClimate.evaluate(sampler, QuartPos.fromBlock((int) Math.floor(source.getPosition().x)),
                QuartPos.fromBlock((int) Math.floor(source.getPosition().y)), QuartPos.fromBlock((int) Math.floor(source.getPosition().z)));
        if (result == null) { source.sendFailure(Component.literal("TerraLatitude is inactive for this sampler/dimension or disabled world profile.")); return 0; }
        var p = result.profile();
        source.sendSuccess(() -> Component.literal("TerraLatitude profile " + p.fingerprint().substring(0, 12)
                + "; NT mode=" + p.naturalTemperature().mode() + "; temperature=" + p.temperatureStrength()
                + "; moisture=" + p.moistureStrength() + "; transition=" + p.transitionRadius() + " blocks."), false);
        return 1;
    }
    private static int sample(CommandSourceStack source) {
        try {
            var row = inspect(source, (int) Math.floor(source.getPosition().x), (int) Math.floor(source.getPosition().z));
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "TerraLatitude (%d, %d, %d): T %.3f, moisture %.3f; ground %d; biome %s. Climate units, not Celsius.",
                    row.x(), row.y(), row.z(), row.temperature(), row.humidity(), row.ground(), row.biome())), false);
            return 1;
        } catch (RuntimeException e) { source.sendFailure(Component.literal(e.getMessage())); return 0; }
    }
    private static int transect(CommandSourceStack source, int radius, int step) {
        int count = 2 * radius / step + 1;
        if (count > 129) { source.sendFailure(Component.literal("Use a larger step: maximum 129 samples per transect.")); return 0; }
        try {
            int x = (int) Math.floor(source.getPosition().x), z = (int) Math.floor(source.getPosition().z);
            StringBuilder csv = new StringBuilder("x,y,z,ground,temperature,humidity,raw_temperature,raw_humidity,band_temperature,band_humidity,biome\n");
            for (int offset = -radius; offset <= radius; offset += step) {
                var row = inspect(source, x, Math.addExact(z, offset));
                csv.append(row.csv()).append('\n');
            }
            Path dir = WorldProfiles.root(source.getServer()).resolve("terralatitude-diagnostics");
            Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, "transect-" + Instant.now().toEpochMilli() + "-", ".csv");
            Files.writeString(file, csv.toString());
            source.sendSuccess(() -> Component.literal("Exported " + count + " north/south surface samples to " + file
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
        var raw = RawClimateAccess.sample(ftf.climateQuerySemantics().surfaceContext(), x, z);
        if (!raw.valid()) throw new IllegalStateException("FTF raw climate is unavailable.");
        int y = raw.surfaceY(), qy = QuartPos.fromBlock(y);
        var evaluation = IntegratedClimate.evaluate(sampler, qx, qy, qz);
        if (evaluation == null) throw new IllegalStateException("TerraLatitude is inactive for this sampler/world.");
        var actual = ftf.getWorldgenPlan().samplerDecoration().sample(sampler, qx, qy, qz);
        var biome = chunkSource.getGenerator().getBiomeSource().getNoiseBiome(qx, qy, qz, sampler);
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse("unregistered");
        return new Row(x, y, z, raw.surfaceY(), actual.temperature() / 10000.0, actual.humidity() / 10000.0,
                raw.temperature(), raw.humidity(), evaluation.bandTemperature(), evaluation.bandHumidity(), id);
    }
    private record Row(int x, int y, int z, int ground, double temperature, double humidity,
                       double rawT, double rawH, double bandT, double bandH, String biome) {
        String csv() { return String.format(Locale.ROOT, "%d,%d,%d,%d,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%s",
                x, y, z, ground, temperature, humidity, rawT, rawH, bandT, bandH, biome); }
    }
}
