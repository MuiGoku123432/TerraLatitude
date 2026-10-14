package dev.fnf.terralatitude;

import java.lang.reflect.Modifier;

/** A PR build shares its version label with upstream; check the actual API at startup. */
public final class FtfApi {
    private FtfApi() {}

    public static void requireSupported() {
        String prefix = "etcodehome.freeterraforged.world.worldgen.cell.";
        ClassLoader loader = FtfApi.class.getClassLoader();
        try {
            Class<?> cell = Class.forName(prefix + "Cell", false, loader);
            Class<?> climate = Class.forName(prefix + "climate.Climate", false, loader);
            var sample = climate.getMethod("sampleRaw", cell, float.class, float.class);
            Class<?> result = sample.getReturnType();
            if (Modifier.isStatic(sample.getModifiers())
                    || !result.getName().equals(prefix + "climate.ClimateModule$RawSample")) {
                throw new NoSuchMethodException("Climate.sampleRaw must return ClimateModule.RawSample");
            }
            for (String axis : new String[]{"temperature", "moisture"}) {
                var accessor = result.getMethod(axis);
                if (Modifier.isStatic(accessor.getModifiers()) || accessor.getReturnType() != float.class) {
                    throw new NoSuchMethodException("RawSample." + axis + " must return float");
                }
            }
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("TerraLatitude requires the FreeTerraForged raw climate API from "
                    + "PR #301 (tested commit 2228f6c5). The FTF version label alone is insufficient; "
                    + "use the matching API build described in TerraLatitude's README.", failure);
        }
    }
}
