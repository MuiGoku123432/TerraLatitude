package dev.fnf.terralatitude;

/** Tests coordinate fidelity; terrain sampling and actual Mixin loading remain runtime tests. */
public final class RawClimateMathTest {
    private static int checks;

    public static void main(String[] args) {
        equal(320.0F, RawClimateMath.edgeDistance(0.0F, 320));
        equal(320.0F, RawClimateMath.edgeDistance(-0.1F, 320));
        equal(160.0F, RawClimateMath.edgeDistance(0.2F, 320));
        equal(0.0F, RawClimateMath.edgeDistance(0.4F, 320));
        equal(0.0F, RawClimateMath.edgeDistance(0.8F, 320));
        equal(0.0F, RawClimateMath.edgeDistance(0.0F, 0));
        float previous = Float.POSITIVE_INFINITY;
        for (int i = 0; i <= 10000; i++) {
            float edge = i / 10000.0F;
            float distance = RawClimateMath.edgeDistance(edge, 320);
            require(distance >= 0.0F && distance <= 320.0F, "bounded edge displacement");
            require(distance <= previous, "edge displacement must not grow towards region center");
            previous = distance;
        }
        // Both warp components must sample the original input; using the already
        // changed x to compute z would distort FTF's coordinate convention.
        RawClimateMath.Coordinates coordinates = RawClimateMath.moduleCoordinates(
                -1000.0F, 750.0F, 0.25F, -0.5F, 64.0F, 0.01F);
        equal((-1000.0F + 0.25F * 64.0F) * 0.01F, coordinates.x());
        equal((750.0F - 0.5F * 64.0F) * 0.01F, coordinates.z());
        equal(-1.0F, RawClimateMath.normalize(0.0F));
        equal(0.0F, RawClimateMath.normalize(0.5F));
        equal(1.0F, RawClimateMath.normalize(1.0F));
        System.out.println("RawClimateMathTest passed " + checks + " assertions");
    }

    private static void equal(float expected, float actual) {
        require(Float.floatToIntBits(expected) == Float.floatToIntBits(actual),
                "expected " + expected + ", got " + actual);
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
