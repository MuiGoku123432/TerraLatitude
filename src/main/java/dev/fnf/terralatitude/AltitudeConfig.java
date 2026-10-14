package dev.fnf.terralatitude;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AltitudeConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.DoubleValue LAPSE_RATE;
    private static final ModConfigSpec.DoubleValue METERS_PER_BLOCK;
    private static final ModConfigSpec.DoubleValue DEGREES_PER_CLIMATE_UNIT;
    private static final ModConfigSpec.BooleanValue PRESERVE_RANDOMIZED_UNDERGROUND;
    private static volatile Snapshot current = new Snapshot(true, AltitudeModel.defaults(), true);

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Experimental FTF + Natural Temperature biome-placement correction.",
                "Keep settings consistent when generating a world; existing biome data is not rewritten.").push("altitude");
        ENABLED = builder.comment("Enable altitude cooling in FTF-owned Overworld generation.")
                .worldRestart().define("enabled", true);
        LAPSE_RATE = builder.comment("Environmental lapse-rate reference in Celsius per effective kilometer.",
                "6.5 is an atmospheric average, not a simulation of local weather.")
                .worldRestart().defineInRange("lapse_rate_c_per_km", 6.5, 0.0, 15.0);
        METERS_PER_BLOCK = builder.comment("Game-scale assumption: effective landscape meters per vertical block.",
                "10 lets a compressed 500-block world represent kilometer-scale mountain relief.")
                .worldRestart().defineInRange("effective_meters_per_block", 10.0, 0.1, 100.0);
        DEGREES_PER_CLIMATE_UNIT = builder.comment("Tuning conversion for the biome-placement climate axis.",
                "Minecraft climate units are not degrees Celsius. Larger values give weaker cooling.")
                .worldRestart().defineInRange("degrees_per_climate_unit", 20.0, 1.0, 100.0);
        PRESERVE_RANDOMIZED_UNDERGROUND = builder.comment("When Natural Temperature explicitly randomizes underground climate, leave those samples unchanged.",
                "Otherwise ground elevation cools the column; query height never determines the cooling amount.")
                .worldRestart().define("preserve_randomized_underground", true);
        builder.pop();
        SPEC = builder.build();
    }

    private AltitudeConfig() {}

    public static Snapshot current() {
        return current;
    }

    public static void refresh() {
        current = new Snapshot(ENABLED.get(), new AltitudeModel.Settings(
                LAPSE_RATE.get(), METERS_PER_BLOCK.get(), DEGREES_PER_CLIMATE_UNIT.get()),
                PRESERVE_RANDOMIZED_UNDERGROUND.get());
        TerraLatitude.LOGGER.info("Altitude settings loaded: enabled={}, lapse={} C/km, scale={} effective m/block, conversion={} C/climate-unit.",
                current.enabled(), current.settings().lapseRateCPerKm(),
                current.settings().effectiveMetersPerBlock(), current.settings().degreesPerClimateUnit());
    }

    public record Snapshot(boolean enabled, AltitudeModel.Settings settings, boolean preserveRandomizedUnderground) {}
}
