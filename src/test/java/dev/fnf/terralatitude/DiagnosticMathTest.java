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
        if (DiagnosticMath.transectCount(8192,128) != 129
                || DiagnosticMath.transectCount(4096,128) != 65
                || DiagnosticMath.transectCount(8192,16) <= 129
                || DiagnosticMath.transectCount(17,16) != 3)
            throw new AssertionError("transect counts/cap changed");
        for (var axis : DiagnosticMath.Axis.values()) {
            int count = 0;
            for (int offset = -4096; offset <= 4096; offset += 128) {
                var p = DiagnosticMath.transectPosition(-123,456,offset,axis);
                if (axis == DiagnosticMath.Axis.NORTH_SOUTH ? p.x()!=-123 || p.z()!=456+offset
                        : p.x()!=-123+offset || p.z()!=456) throw new AssertionError("transect axis");
                count++;
            }
            if (count != 65) throw new AssertionError("transect loop count");
        }
        try {
            DiagnosticMath.transectPosition(Integer.MAX_VALUE,0,1,DiagnosticMath.Axis.EAST_WEST);
            throw new AssertionError("coordinate overflow accepted");
        } catch (ArithmeticException expected) {}
        try {
            DiagnosticMath.transectCount(16,0);
            throw new AssertionError("zero step accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("DiagnosticMathTest passed (surface alignment, both transect axes, cap and overflow)");
    }
}
