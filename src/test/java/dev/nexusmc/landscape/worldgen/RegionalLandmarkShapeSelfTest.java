package dev.nexusmc.landscape.worldgen;

import java.util.HashSet;
import java.util.Set;

/** Guards deterministic, non-spherical regional outcrop silhouettes. */
public final class RegionalLandmarkShapeSelfTest {
    private RegionalLandmarkShapeSelfTest() {
    }

    public static void main(String[] args) {
        long seed = 0x414E47554C41524CL;
        Set<Point> first = points(seed, true);
        Set<Point> repeat = points(seed, true);
        Set<Point> rotated = points(seed, false);

        require(first.equals(repeat), "outcrop shape is not deterministic");
        require(!first.equals(rotated), "outcrop orientation has no effect");
        require(first.contains(new Point(0, 0, 0)),
            "outcrop lost its structural center");
        require(first.stream().anyMatch(point -> point.y() >= 3),
            "outcrop collapsed vertically");
        require(first.stream().anyMatch(point -> point.x() >= 3),
            "outcrop lost directional rise");
        require(first.stream().noneMatch(point -> point.y() == 4
            && point.x() < 0),
            "outcrop retained a symmetric rounded cap");
        System.out.println("RegionalLandmarkShapeSelfTest: PASS voxels="
            + first.size());
    }

    private static Set<Point> points(long seed, boolean alongX) {
        Set<Point> result = new HashSet<>();
        for (int x = -5; x <= 6; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -5; z <= 6; z++) {
                    if (RegionalLandmarkShape.angularOutcrop(
                        seed, x, y, z, 4, 3, 4, alongX
                    )) {
                        result.add(new Point(x, y, z));
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Point(int x, int y, int z) {
    }
}
