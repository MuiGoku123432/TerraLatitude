package dev.fnf.terralatitude;

import com.mojang.serialization.JsonOps;
import etcodehome.freeterraforged.data.worldgen.preset.settings.Preset;
import etcodehome.freeterraforged.world.worldgen.biome.FTFClimateSampler;
import etcodehome.freeterraforged.world.worldgen.runtime.WorldgenOwnerType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** World-lifetime registry. No worldgen thread ever changes Natural Temperature globals. */
public final class WorldProfiles {
    private static volatile Session current;
    private WorldProfiles() {}
    public static void starting(ServerAboutToStartEvent event) {
        current = new Session(event.getServer());
    }
    public static void stopped(ServerStoppedEvent event) {
        Session s = current;
        if (s != null && s.server == event.getServer()) current = null;
    }
    public static ClimateProfile forSampler(FTFClimateSampler sampler) {
        Session s = current;
        var plan = sampler.getWorldgenPlan();
        if (s == null || plan == null || plan.owner() == null
                || plan.owner().type() != WorldgenOwnerType.WORLDGEN_EPOCH
                || plan.owner().seed() != s.seed || plan.owner().registries() == null
                || plan.owner().registries().registryOrThrow(Registries.BIOME) != s.biomes
                || plan.owner().registries().registryOrThrow(Registries.LEVEL_STEM) != s.stems
                || plan.owner().selectedStem() == null
                || plan.owner().selectedStem().generator() != s.generator) return null;
        if (s.profile == null && !s.defaults.enabled()) return null;
        return s.bind(sampler);
    }
    public static ClimateProfile active(MinecraftServer server) {
        Session s = current;
        return s != null && s.server == server ? s.profile : null;
    }
    public static Path root(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT); }
    private static final class Session {
        private final MinecraftServer server;
        private final long seed;
        private final Registry<Biome> biomes;
        private final Registry<LevelStem> stems;
        private final ChunkGenerator generator;
        private final Path file;
        private final IntegrationConfig.Defaults defaults;
        private final ConcurrentHashMap<UUID, ClimateProfile> bindings = new ConcurrentHashMap<>();
        private final NtSettings newWorldSettings;
        private volatile ClimateProfile profile;
        private Session(MinecraftServer server) {
            this.server = server;
            seed = server.getWorldData().worldGenOptions().seed();
            // FTF freezes RegistryAccess into a new wrapper. Individual registries
            // and the selected generator preserve identity across that operation.
            biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
            stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            generator = java.util.Objects.requireNonNull(stems.get(LevelStem.OVERWORLD)).generator();
            file = root(server).resolve("terralatitude-climate.json");
            defaults = IntegrationConfig.current();
            if (Files.exists(file)) {
                try { profile = ProfileFiles.read(file); }
                catch (IOException e) { throw new UncheckedIOException("Cannot read world climate profile " + file, e); }
                catch (RuntimeException e) { throw new IllegalStateException("Invalid world climate profile " + file, e); }
                if (profile.naturalTemperature().seed() != seed)
                    throw new IllegalStateException("World climate profile seed differs from this world: " + file);
                TerraLatitude.LOGGER.info("Loaded saved TerraLatitude world profile {}", profile.fingerprint());
            }
            newWorldSettings = profile == null && defaults.enabled() ? NtSettings.capture().withSeed(seed) : null;
        }
        private ClimateProfile bind(FTFClimateSampler sampler) {
            UUID id = sampler.getWorldgenPlan().owner().id();
            ClimateProfile bound = bindings.get(id);
            if (bound != null) return bound;
            synchronized (this) {
                bound = bindings.get(id);
                if (bound != null) return bound;
                var semantics = sampler.climateQuerySemantics();
                if (semantics == null || semantics.preset() == null) return null;
                Preset preset = semantics.preset();
                String fingerprint = ClimateProfile.digest(Preset.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, preset).getOrThrow().toString());
                if (profile == null) {
                    ClimateProfile created = new ClimateProfile(defaults.enabled(), defaults.temperatureStrength(), defaults.moistureStrength(),
                            defaults.transitionRadius(), preset.climate().altitudeCoolingStrength, fingerprint,
                            newWorldSettings);
                    try {
                        ProfileFiles.create(file, created);
                    } catch (IOException e) { throw new UncheckedIOException("Cannot save world climate profile " + file, e); }
                    profile = created;
                    TerraLatitude.LOGGER.info("Created TerraLatitude world profile {} at {}", profile.fingerprint(), file);
                } else if (!fingerprint.equals(profile.presetFingerprint())) {
                    // A saved NT profile cannot freeze FTF's terrain and noise graphs.
                    throw new IllegalStateException("FTF preset differs from saved TerraLatitude profile. Restore the original preset or deliberately prepare a new test world: " + file);
                }
                bindings.put(id, profile);
                return profile;
            }
        }
    }
}
