package dev.nexusmc.landscape.worldgen.v2.hydrology;

/**
 * Shared stream-order width policy for hydrology sampling and carving.
 * Low-order headwaters become narrow streams while established rivers retain
 * the wider Alpha 1 cross-sections.
 */
public final class RiverWidthPolicy {
    private RiverWidthPolicy() {
    }

    public static double halfWidth(int order) {
        if (order < 0) {
            throw new IllegalArgumentException("stream order must be non-negative");
        }
        return switch (order) {
            case 0 -> 3.5;
            case 1 -> 5.5;
            case 2 -> 10.0;
            default -> 17.0 + order * 7.0;
        };
    }

    public static double legacyHalfWidth(int order) {
        if (order < 0) {
            throw new IllegalArgumentException("stream order must be non-negative");
        }
        return 17.0 + order * 7.0;
    }

    public static double distanceScale(int order) {
        return legacyHalfWidth(order) / halfWidth(order);
    }

    public static double mask(double distance, int order) {
        double halfWidth = halfWidth(order);
        return 1.0 - smoothstep(halfWidth, halfWidth * 4.2, distance);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
