package dev.fnf.terralatitude;

public final class DiagnosticMathTest {
    private static void expect(int ground, int water, int expected) {
        int actual = DiagnosticMath.surfaceSampleY(ground, water);
        if (actual != expected) throw new AssertionError("surface(" + ground + "," + water + ")=" + actual + ", expected " + expected);
    }

    public static void main(String[] args) {
        expect(-54, 62, 64); // Ocean floor must not become the surface query height.
        expect(62, 62, 64);
        expect(63, 62, 64);
        expect(64, 62, 64);
        expect(65, 62, 68);
        expect(256, 62, 256);
        expect(267, 62, 268);
        expect(-65, -62, -60); // Negative elevations still round upward.
        expect(-63, -64, -60);
        expect(-64, -64, -64);
        expect(-4, -64, -4);
        expect(-1, -64, 0);
        expect(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        expect(Integer.MAX_VALUE - 3, 62, Integer.MAX_VALUE - 3);
        try {
            DiagnosticMath.surfaceSampleY(Integer.MAX_VALUE, 62);
            throw new AssertionError("unrepresentable surface coordinate must fail clearly");
        } catch (ArithmeticException expected) {}
        System.out.println("DiagnosticMathTest passed (15 cases)");
    }
}
