package dev.fnf.terralatitude;

/** Coordinate rules for surface diagnostics; independent of game classes. */
public final class DiagnosticMath {
    private DiagnosticMath() {}

    /** First quart-grid block Y at or above both the terrain and the water surface. */
    public static int surfaceSampleY(int groundY, int seaLevel) {
        long surfaceY = Math.max(groundY, seaLevel);
        return Math.toIntExact(Math.floorDiv(surfaceY + 3L, 4L) * 4L);
    }
}
