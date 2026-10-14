package dev.fnf.terralatitude;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Defaults for new world profiles; editing these does not rewrite an existing world. */
public final class IntegrationConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.DoubleValue TEMPERATURE, MOISTURE;
    private static final ModConfigSpec.IntValue TRANSITION_RADIUS;
    private static volatile Defaults current = new Defaults(true, 1.0, 0.5, 128);
    static {
        var b = new ModConfigSpec.Builder();
        b.comment("Defaults copied once into <world>/terralatitude-climate.json.",
                "Existing worlds keep their saved settings. Restart before creating a comparison world.").push("climate");
        ENABLED = b.worldRestart().define("enabled", true);
        TEMPERATURE = b.comment("Temperature latitude-band strength, multiplied by saved NT influence.")
                .worldRestart().defineInRange("temperature_band_strength", 1.0, 0.0, 1.0);
        MOISTURE = b.comment("Moisture latitude-band strength. Remaining weight retains FTF terrain moisture.",
                "Also multiplied by saved NT influence; zero NT influence preserves native FTF climate.")
                .worldRestart().defineInRange("moisture_band_strength", 0.5, 0.0, 1.0);
        TRANSITION_RADIUS = b.comment("Smooth only the NT climate field over this block radius; zero keeps NT transitions exactly.",
                "Reuses NT waves and FTF regional noise without adding a new noise field.")
                .worldRestart().defineInRange("transition_radius_blocks", 128, 0, 2048);
        b.pop();
        SPEC = b.build();
    }
    private IntegrationConfig() {}
    public static Defaults current() { return current; }
    public static void refresh() {
        current = new Defaults(ENABLED.get(), TEMPERATURE.get(), MOISTURE.get(), TRANSITION_RADIUS.get());
    }
    public record Defaults(boolean enabled, double temperatureStrength, double moistureStrength, int transitionRadius) {}
}
