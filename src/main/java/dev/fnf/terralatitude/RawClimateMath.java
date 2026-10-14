package dev.fnf.terralatitude;

/** Float arithmetic matching the pinned FTF regional climate coordinate path. */
public final class RawClimateMath {
    private RawClimateMath() {}

    public record Coordinates(float x, float z) {}

    public static float edgeDistance(float regionEdge, int offsetDistance) {
        float clamped = Math.max(0.0F, Math.min(0.4F, regionEdge));
        // This ordering mirrors NoiseUtil.map(edge, 0, 0.4, 0.4).
        float mapped = clamped >= 0.4F ? 1.0F : clamped / 0.4F;
        return offsetDistance * (1.0F - mapped);
    }

    public static Coordinates moduleCoordinates(float x, float z, float warpX,
                                                float warpZ, float strength, float frequency) {
        return new Coordinates((x + warpX * strength) * frequency,
                (z + warpZ * strength) * frequency);
    }

    public static float normalize(float zeroToOne) {
        return zeroToOne * 2.0F - 1.0F;
    }
}
