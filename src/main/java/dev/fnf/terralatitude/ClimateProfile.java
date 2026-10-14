package dev.fnf.terralatitude;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Immutable, versioned world-owned settings. Persisted independently of mod globals. */
public record ClimateProfile(boolean enabled, double temperatureStrength, double moistureStrength,
                             int transitionRadius, double altitudeStrength, String presetFingerprint,
                             NtSettings naturalTemperature) {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public ClimateProfile {
        check(temperatureStrength, 0, 1, "temperature strength");
        check(moistureStrength, 0, 1, "moisture strength");
        check(altitudeStrength, 0, 32, "altitude strength");
        if (transitionRadius < 0 || transitionRadius > 2048) throw new IllegalArgumentException("Invalid transition radius");
        if (naturalTemperature == null || presetFingerprint == null || presetFingerprint.isBlank())
            throw new IllegalArgumentException("Missing NT snapshot or FTF preset identity");
    }
    private static void check(double v, double min, double max, String name) {
        if (!Double.isFinite(v) || v < min || v > max) throw new IllegalArgumentException("Invalid " + name);
    }
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("enabled", enabled);
        root.addProperty("temperature_band_strength", temperatureStrength);
        root.addProperty("moisture_band_strength", moistureStrength);
        root.addProperty("transition_radius_blocks", transitionRadius);
        root.addProperty("ftf_altitude_strength", altitudeStrength);
        root.addProperty("ftf_preset_fingerprint", presetFingerprint);
        JsonObject nt = new JsonObject();
        new TreeMap<>(naturalTemperature.toMap()).forEach(nt::addProperty);
        root.add("natural_temperature", nt);
        return JSON.toJson(root) + "\n";
    }
    public static ClimateProfile fromJson(String text) {
        JsonObject r = JsonParser.parseString(text).getAsJsonObject();
        var expected = java.util.Set.of("schema", "enabled", "temperature_band_strength", "moisture_band_strength",
                "transition_radius_blocks", "ftf_altitude_strength", "ftf_preset_fingerprint", "natural_temperature");
        if (!r.keySet().equals(expected)) throw new IllegalArgumentException("World climate profile has missing or unknown fields");
        if (integer(r, "schema") != 1) throw new IllegalArgumentException("Unsupported world climate schema");
        Map<String, String> nt = new TreeMap<>();
        r.getAsJsonObject("natural_temperature").entrySet().forEach(e -> {
            if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("NT snapshot values must be strings");
            nt.put(e.getKey(), e.getValue().getAsString());
        });
        return new ClimateProfile(bool(r, "enabled"), number(r, "temperature_band_strength"),
                number(r, "moisture_band_strength"), integer(r, "transition_radius_blocks"),
                number(r, "ftf_altitude_strength"), r.get("ftf_preset_fingerprint").getAsString(),
                NtSettings.fromMap(nt));
    }
    private static double number(JsonObject r, String key) {
        if (!r.get(key).isJsonPrimitive() || !r.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Expected numeric " + key);
        double value = r.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite " + key);
        return value;
    }
    private static int integer(JsonObject r, String key) {
        number(r, key);
        try { return r.get(key).getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Expected integer " + key, e); }
    }
    private static boolean bool(JsonObject r, String key) {
        if (!r.get(key).isJsonPrimitive() || !r.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException("Expected boolean " + key);
        return r.get(key).getAsBoolean();
    }
    public String fingerprint() { return digest(toJson()); }
    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }
}
