package dev.fnf.terralatitude;

/** Coordinate rules for surface diagnostics; independent of game classes. */
public final class DiagnosticMath {
    private DiagnosticMath() {}

    public enum Axis {
        NORTH_SOUTH("north/south"), EAST_WEST("east/west");
        private final String label;
        Axis(String label) { this.label = label; }
        public String label() { return label; }
    }
    public record Position(int x, int z) {}

    public static int transectCount(int radius, int step) {
        if (radius < 0 || step <= 0) throw new IllegalArgumentException("Invalid transect radius/step");
        return Math.toIntExact(2L * radius / step + 1L);
    }

    public static Position transectPosition(int x, int z, int offset, Axis axis) {
        return switch (axis) {
            case NORTH_SOUTH -> new Position(x, Math.addExact(z, offset));
            case EAST_WEST -> new Position(Math.addExact(x, offset), z);
        };
    }

    /** First quart-grid block Y at or above both the terrain and the water surface. */
    public static int surfaceSampleY(int groundY, int seaLevel) {
        long surfaceY = Math.max(groundY, seaLevel);
        return Math.toIntExact(Math.floorDiv(surfaceY + 3L, 4L) * 4L);
    }
}
