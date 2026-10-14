package dev.fnf.terralatitude;

import com.naturaltemperature.Config;
import com.naturaltemperature.NoiseBiomeBandsGenerator;
import com.naturaltemperature.WorldSeedHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable NT configuration captured once for an owning world, never written back to Config. */
public final class NtSettings {
    private static final Set<String> DOUBLE_KEYS = Set.of(
            "equatorial_distance",
            "equator_offset",
            "randomize_underground_below_y",
            "global_temperature_modifier_percentage",
            "global_temperature_mitigation_percentage",
            "wave_magnification",
            "humidity_offset",
            "humidity_bands_width",
            "natural_temperature_influence",
            "temperature_zone_1",
            "temperature_zone_2",
            "temperature_zone_3",
            "temperature_zone_4",
            "temperature_zone_5",
            "temperature_zone_6",
            "humidity_zone_1",
            "humidity_zone_2",
            "humidity_zone_3",
            "humidity_zone_4",
            "humidity_zone_5",
            "humidity_zone_6",
            "percentage_coverage_zone_1",
            "percentage_coverage_zone_2",
            "percentage_coverage_zone_3",
            "percentage_coverage_zone_4",
            "percentage_coverage_zone_5",
            "percentage_coverage_zone_6");
    private static final Set<String> BOOLEAN_KEYS = Set.of(
            "looping_world",
            "randomize_underground",
            "multidimensional_bands",
            "extra_bands",
            "enable_humidity_bands");
    private static final Set<String> INTEGER_KEYS = Set.of(
            "generation_mode",
            "underground_randomization_type");
    private static final Set<String> STRING_KEYS = Set.of(
            "custom_mode_id_1",
            "custom_mode_id_2",
            "custom_mode_id_3",
            "custom_mode_id_4",
            "custom_mode_id_5");
    private final Map<String, String> values;
    private final Map<String, Double> doubles;
    private final Map<String, Boolean> booleans;
    private final Map<String, Integer> integers;
    private final long seed;
    private volatile NoiseBiomeBandsGenerator noise;

    private NtSettings(Map<String, String> input) {
        Objects.requireNonNull(input, "NT settings");
        Map<String, String> copy = new LinkedHashMap<>(input);
        if (!"1".equals(copy.get("snapshot_schema"))) throw new IllegalArgumentException("Unsupported NT snapshot schema");
        seed = Long.parseLong(required(copy, "world_seed"));
        Map<String, Double> numbers = new LinkedHashMap<>();
        for (String key : DOUBLE_KEYS) {
            double value = Double.parseDouble(required(copy, key));
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite NT setting: " + key);
            numbers.put(key, value);
        }
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String key : BOOLEAN_KEYS) {
            String value = required(copy, key);
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean: " + key);
            flags.put(key, Boolean.parseBoolean(value));
        }
        Map<String, Integer> whole = new LinkedHashMap<>();
        for (String key : INTEGER_KEYS) whole.put(key, Integer.parseInt(required(copy, key)));
        for (String key : STRING_KEYS) required(copy, key);
        for (String key : copy.keySet()) {
            if (!DOUBLE_KEYS.contains(key) && !BOOLEAN_KEYS.contains(key) && !INTEGER_KEYS.contains(key)
                    && !STRING_KEYS.contains(key) && !key.equals("world_seed") && !key.equals("snapshot_schema")) {
                throw new IllegalArgumentException("Unknown NT setting: " + key);
            }
        }
        if (numbers.get("equatorial_distance") < 0.0) throw new IllegalArgumentException("NT equatorial distance cannot be negative");
        if (numbers.get("natural_temperature_influence") < 0.0 || numbers.get("natural_temperature_influence") > 100.0) {
            throw new IllegalArgumentException("NT influence must be between 0 and 100");
        }
        int mode = whole.get("generation_mode");
        if (mode < 0 || mode > 10) {
            throw new IllegalArgumentException("TerraLatitude supports NT built-in modes 0..10; custom modes cannot guarantee world-isolated settings: " + mode);
        }
        int randomization = whole.get("underground_randomization_type");
        if (randomization < 0 || randomization > 2) throw new IllegalArgumentException("Unsupported NT underground randomization type");
        double coverage4 = 0.0, coverage6 = 0.0;
        for (int i = 1; i <= 6; i++) {
            double coverage = numbers.get("percentage_coverage_zone_" + i);
            if (i <= 4) coverage4 += coverage;
            coverage6 += coverage;
        }
        if ((mode == 3 || mode == 4) && coverage4 == 0.0
                || mode >= 5 && coverage6 == 0.0) throw new IllegalArgumentException("Selected NT mode needs nonzero total zone coverage");
        values = Map.copyOf(copy);
        doubles = Map.copyOf(numbers);
        booleans = Map.copyOf(flags);
        integers = Map.copyOf(whole);
    }

    private static String required(Map<String, String> values, String key) {
        return Objects.requireNonNull(values.get(key), "Missing saved NT setting: " + key);
    }

    /** Capture after NT config loading; bind the correct FTF seed with withSeed before saving. */
    public static NtSettings capture() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("equatorial_distance", String.valueOf(Config.equatorial_distance));
        values.put("equator_offset", String.valueOf(Config.equator_offset));
        values.put("randomize_underground_below_y", String.valueOf(Config.randomize_underground_below_y));
        values.put("global_temperature_modifier_percentage", String.valueOf(Config.global_temperature_modifier_percentage));
        values.put("global_temperature_mitigation_percentage", String.valueOf(Config.global_temperature_mitigation_percentage));
        values.put("wave_magnification", String.valueOf(Config.wave_magnification));
        values.put("humidity_offset", String.valueOf(Config.humidity_offset));
        values.put("humidity_bands_width", String.valueOf(Config.humidity_bands_width));
        values.put("natural_temperature_influence", String.valueOf(Config.natural_temperature_influence));
        values.put("temperature_zone_1", String.valueOf(Config.temperature_zone_1));
        values.put("temperature_zone_2", String.valueOf(Config.temperature_zone_2));
        values.put("temperature_zone_3", String.valueOf(Config.temperature_zone_3));
        values.put("temperature_zone_4", String.valueOf(Config.temperature_zone_4));
        values.put("temperature_zone_5", String.valueOf(Config.temperature_zone_5));
        values.put("temperature_zone_6", String.valueOf(Config.temperature_zone_6));
        values.put("humidity_zone_1", String.valueOf(Config.humidity_zone_1));
        values.put("humidity_zone_2", String.valueOf(Config.humidity_zone_2));
        values.put("humidity_zone_3", String.valueOf(Config.humidity_zone_3));
        values.put("humidity_zone_4", String.valueOf(Config.humidity_zone_4));
        values.put("humidity_zone_5", String.valueOf(Config.humidity_zone_5));
        values.put("humidity_zone_6", String.valueOf(Config.humidity_zone_6));
        values.put("percentage_coverage_zone_1", String.valueOf(Config.percentage_coverage_zone_1));
        values.put("percentage_coverage_zone_2", String.valueOf(Config.percentage_coverage_zone_2));
        values.put("percentage_coverage_zone_3", String.valueOf(Config.percentage_coverage_zone_3));
        values.put("percentage_coverage_zone_4", String.valueOf(Config.percentage_coverage_zone_4));
        values.put("percentage_coverage_zone_5", String.valueOf(Config.percentage_coverage_zone_5));
        values.put("percentage_coverage_zone_6", String.valueOf(Config.percentage_coverage_zone_6));
        values.put("looping_world", String.valueOf(Config.looping_world));
        values.put("randomize_underground", String.valueOf(Config.randomize_underground));
        values.put("multidimensional_bands", String.valueOf(Config.multidimensional_bands));
        values.put("extra_bands", String.valueOf(Config.extra_bands));
        values.put("enable_humidity_bands", String.valueOf(Config.enable_humidity_bands));
        values.put("generation_mode", String.valueOf(Config.generation_mode));
        values.put("underground_randomization_type", String.valueOf(Config.underground_randomization_type));
        values.put("custom_mode_id_1", String.valueOf(Config.custom_mode_id_1));
        values.put("custom_mode_id_2", String.valueOf(Config.custom_mode_id_2));
        values.put("custom_mode_id_3", String.valueOf(Config.custom_mode_id_3));
        values.put("custom_mode_id_4", String.valueOf(Config.custom_mode_id_4));
        values.put("custom_mode_id_5", String.valueOf(Config.custom_mode_id_5));
        values.put("snapshot_schema", "1");
        values.put("world_seed", Long.toString(WorldSeedHolder.getSeed()));
        return new NtSettings(values);
    }

    public static NtSettings fromMap(Map<String, String> values) { return new NtSettings(values); }
    public Map<String, String> toMap() { return values; }
    public NtSettings withSeed(long value) {
        if (seed == value) return this;
        Map<String, String> copy = new LinkedHashMap<>(values);
        copy.put("world_seed", Long.toString(value));
        return new NtSettings(copy);
    }
    public long seed() { return seed; }
    public int mode() { return integer("generation_mode"); }
    public double influence() { return number("natural_temperature_influence") / 100.0; }
    public boolean undergroundEnabled() { return flag("randomize_underground"); }
    public double undergroundBelowY() { return number("randomize_underground_below_y"); }
    public double number(String key) { return Objects.requireNonNull(doubles.get(key), key); }
    public boolean flag(String key) { return Objects.requireNonNull(booleans.get(key), key); }
    public int integer(String key) { return Objects.requireNonNull(integers.get(key), key); }

    double noise(int blockX, int blockZ) {
        NoiseBiomeBandsGenerator local = noise;
        if (local == null) {
            synchronized (this) {
                local = noise;
                if (local == null) noise = local = new NoiseBiomeBandsGenerator(seed);
            }
        }
        return local.getNoise(blockX, blockZ, 6.0);
    }
}
