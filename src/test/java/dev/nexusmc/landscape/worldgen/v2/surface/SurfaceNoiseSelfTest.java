package dev.nexusmc.landscape.worldgen.v2.surface;

/**
 * Lightweight regression checks for deterministic Stage 6 surface noise.
 */
public final class SurfaceNoiseSelfTest {
    private SurfaceNoiseSelfTest() {
    }

    public static void main(String[] args) {
        deterministicValue();
        boundedRange();
        deterministicHash();
        rejectsInvalidScale();
    }

    private static void deterministicValue() {
        double first = SurfaceNoise.value(1234L, 48, -91, 32, 0x51FACEL);
        double second = SurfaceNoise.value(1234L, 48, -91, 32, 0x51FACEL);
        require(Double.doubleToLongBits(first) == Double.doubleToLongBits(second),
            "surface noise must be deterministic");
    }

    private static void boundedRange() {
        for (int z = -96; z <= 96; z += 7) {
            for (int x = -96; x <= 96; x += 5) {
                double value = SurfaceNoise.value(987654321L, x, z, 24, 0x6D47E21L);
                require(Double.isFinite(value), "surface noise must remain finite");
                require(value >= 0.0 && value <= 1.0,
                    "surface noise must stay in [0, 1]");
            }
        }
    }

    private static void deterministicHash() {
        long first = SurfaceNoise.hash(77L, -12, 49, 991L);
        long second = SurfaceNoise.hash(77L, -12, 49, 991L);
        require(first == second, "surface hash must be deterministic");
        require(first != SurfaceNoise.hash(77L, -11, 49, 991L),
            "adjacent coordinates should not collapse to one hash");
    }

    private static void rejectsInvalidScale() {
        expectInvalidScale(0);
        expectInvalidScale(-1);
        expectInvalidScale(Integer.MIN_VALUE);
    }

    private static void expectInvalidScale(int scale) {
        try {
            SurfaceNoise.value(1L, 0, 0, scale, 2L);
            throw new AssertionError("scale " + scale + " must be rejected");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage() != null
                    && expected.getMessage().contains("scale"),
                "invalid scale error should explain the cause");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
