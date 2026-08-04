package dev.nexusmc.landscape.worldgen.v2.hydrology;

/** Standalone regression checks for stream-order channel widths. */
public final class RiverWidthPolicySelfTest {
    private RiverWidthPolicySelfTest() {
    }

    public static void main(String[] args) {
        requireClose(RiverWidthPolicy.halfWidth(0), 3.5, "order 0 stream");
        requireClose(RiverWidthPolicy.halfWidth(1), 5.5, "order 1 stream");
        requireClose(RiverWidthPolicy.halfWidth(2), 10.0, "order 2 river");
        requireClose(RiverWidthPolicy.halfWidth(3), 38.0, "order 3 legacy river");
        requireClose(RiverWidthPolicy.halfWidth(4), 45.0, "order 4 legacy river");

        require(RiverWidthPolicy.halfWidth(0) < RiverWidthPolicy.halfWidth(1),
            "headwater width must increase with order");
        require(RiverWidthPolicy.halfWidth(1) < RiverWidthPolicy.halfWidth(2),
            "small river width must increase with order");
        require(RiverWidthPolicy.halfWidth(2) < RiverWidthPolicy.halfWidth(3),
            "established rivers must remain wider than streams");

        requireClose(RiverWidthPolicy.mask(0.0, 0), 1.0, "center mask");
        require(RiverWidthPolicy.mask(3.5, 0) > 0.99,
            "stream mask must remain full at the channel edge");
        require(RiverWidthPolicy.mask(14.7, 0) < 0.01,
            "stream mask must decay outside its influence envelope");
        require(RiverWidthPolicy.distanceScale(0) > RiverWidthPolicy.distanceScale(1),
            "narrower streams must receive stronger distance scaling");

        boolean rejected = false;
        try {
            RiverWidthPolicy.halfWidth(-1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "negative stream order must be rejected");

        System.out.println("RiverWidthPolicySelfTest passed");
    }

    private static void requireClose(double actual, double expected, String name) {
        if (Math.abs(actual - expected) > 1.0E-9) {
            throw new AssertionError(name + ": expected=" + expected
                + " actual=" + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
