package dev.nexusmc.landscape.worldgen;

/** Pure coordinate policy for deliberately angular regional rock outcrops. */
final class RegionalLandmarkShape {
    private RegionalLandmarkShape() {
    }

    static boolean angularOutcrop(
        long seed,
        int x,
        int y,
        int z,
        int radiusX,
        int radiusZ,
        int height,
        boolean alongX
    ) {
        if (radiusX < 2 || radiusZ < 2 || height < 2
            || y < -1 || y > height) {
            return false;
        }

        int rise = Math.max(0, y);
        int layerX = Math.max(1, radiusX - (rise + 1) / 2);
        int layerZ = Math.max(1, radiusZ - rise / 2);
        int shift = rise / 2;
        int shiftedX = x - (alongX ? shift : 0);
        int shiftedZ = z - (alongX ? 0 : shift);
        if (Math.abs(shiftedX) > layerX || Math.abs(shiftedZ) > layerZ) {
            return false;
        }

        int edgeDistance = Math.abs(shiftedX) + Math.abs(shiftedZ);
        if (edgeDistance >= layerX + layerZ) {
            return false;
        }
        boolean boundary = Math.abs(shiftedX) == layerX
            || Math.abs(shiftedZ) == layerZ;
        return !boundary || unit(seed, x, y, z) >= 0.28;
    }

    private static double unit(long seed, int x, int y, int z) {
        long value = seed
            ^ (long)x * 0x632BE59BD9B4E019L
            ^ (long)y * 0x9E3779B185EBCA87L
            ^ (long)z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
