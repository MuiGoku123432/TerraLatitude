package dev.fnf.terralatitude;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public final class ClimateProfileTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static NtSettings fixture() {
        Map<String,String> map = new HashMap<>();
        for (String key : new String[]{"equatorial_distance","equator_offset","randomize_underground_below_y",
                "global_temperature_modifier_percentage","global_temperature_mitigation_percentage","wave_magnification",
                "humidity_offset","humidity_bands_width","natural_temperature_influence"}) map.put(key,"0.0");
        map.put("equatorial_distance","50000"); map.put("humidity_bands_width","1000"); map.put("natural_temperature_influence","100");
        for (int i=1;i<=6;i++) {
            map.put("temperature_zone_"+i,"0.25");map.put("humidity_zone_"+i,"0.5");map.put("percentage_coverage_zone_"+i,"16.666");
        }
        for(String key:new String[]{"looping_world","randomize_underground","multidimensional_bands","extra_bands","enable_humidity_bands"}) map.put(key,"false");
        map.put("generation_mode","0");map.put("underground_randomization_type","0");
        for(int i=1;i<=5;i++)map.put("custom_mode_id_"+i,"unused");
        map.put("snapshot_schema","1");map.put("world_seed","12345");
        return NtSettings.fromMap(map);
    }
    private static void reject(JsonObject object, String label) {
        try {ClimateProfile.fromJson(object.toString()); throw new AssertionError("Accepted "+label);}
        catch (IllegalArgumentException | IllegalStateException expected) {checks++;}
    }
    public static void main(String[] args) throws Exception {
        var profile=new ClimateProfile(true,1,.5,128,2,"preset-test",fixture());
        var copy=ClimateProfile.fromJson(profile.toJson());
        check(copy.toJson().equals(profile.toJson()),"stable complete roundtrip");
        check(copy.fingerprint().equals(profile.fingerprint()),"stable fingerprint");
        check(copy.naturalTemperature().seed()==12345,"seed persisted");
        check(copy.naturalTemperature().toMap().equals(profile.naturalTemperature().toMap()),"all NT settings persisted");
        try {copy.naturalTemperature().toMap().put("equator_offset","900");throw new AssertionError("mutable NT snapshot");}
        catch(UnsupportedOperationException expected){checks++;}
        for(String key:new String[]{"schema","transition_radius_blocks"}) {
            var r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.addProperty(key,1.9);reject(r,"fractional "+key);
            r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.addProperty(key,"1");reject(r,"string "+key);
        }
        var r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.addProperty("enabled","true");reject(r,"string boolean");
        r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.addProperty("schema",2);reject(r,"unknown schema");
        r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.addProperty("moisture_band_strength",-1);reject(r,"negative strength");
        r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.addProperty("temperature_band_strength",1.1);reject(r,"excessive strength");
        r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.addProperty("unknown",3);reject(r,"unknown field");
        r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.remove("enabled");reject(r,"missing field");
        r=JsonParser.parseString(profile.toJson()).getAsJsonObject();r.getAsJsonObject("natural_temperature").addProperty("equatorial_distance",0);reject(r,"untyped NT value");
        var changed=new ClimateProfile(true,1,.6,128,2,"preset-test",fixture());
        check(!changed.fingerprint().equals(profile.fingerprint()),"behavior change alters fingerprint");
        var folder=Files.createTempDirectory("terralatitude-profile-test-");var file=folder.resolve("profile.json");
        try {
            ProfileFiles.create(file,profile);
            check(ProfileFiles.read(file).fingerprint().equals(profile.fingerprint()),"disk roundtrip");
            try {ProfileFiles.create(file,changed);throw new AssertionError("overwrote existing profile");}
            catch(java.io.IOException expected){checks++;}
            check(ProfileFiles.read(file).fingerprint().equals(profile.fingerprint()),"refused overwrite preserves existing file");
            try(var files=Files.list(folder)){check(files.count()==1,"temporary file cleaned up");}
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(folder);}
        System.out.println("ClimateProfileTest passed ("+checks+" assertions)");
    }
}
