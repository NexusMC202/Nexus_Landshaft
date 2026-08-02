package dev.nexusmc.landscape.worldgen.v2.surface;

/**
 * Stateless seed/coordinate noise used for palette dithering and broad
 * transition masks. It has no chunk-coordinate branch, so x=15/16 and z=15/16
 * are ordinary adjacent samples.
 */
public final class SurfaceNoise {
    private SurfaceNoise() {
    }

    public static double value(
        long seed,
        int blockX,
        int blockZ,
        int scale,
        long salt
    ) {
        if (scale <= 0) {
            throw new IllegalArgumentException("scale must be positive: " + scale);
        }

        int cellX = Math.floorDiv(blockX, scale);
        int cellZ = Math.floorDiv(blockZ, scale);
        double tx = smooth(Math.floorMod(blockX, scale) / (double)scale);
        double tz = smooth(Math.floorMod(blockZ, scale) / (double)scale);
        double a = hashUnit(seed, cellX, cellZ, salt);
        double b = hashUnit(seed, cellX + 1, cellZ, salt);
        double c = hashUnit(seed, cellX, cellZ + 1, salt);
        double d = hashUnit(seed, cellX + 1, cellZ + 1, salt);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    public static long hash(long seed, int x, int z, long salt) {
        long value = seed ^ salt;
        value ^= (long)x * 0x9E3779B97F4A7C15L;
        value ^= (long)z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double hashUnit(long seed, int x, int z, long salt) {
        return (hash(seed, x, z, salt) >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double a, double b, double value) {
        return a + (b - a) * value;
    }
}
