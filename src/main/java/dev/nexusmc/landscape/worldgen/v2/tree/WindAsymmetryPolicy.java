package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Shared directional wind response for branch growth and foliage planning.
 * Positive projection points with the wind (leeward); negative projection
 * points into the wind (windward).
 */
public final class WindAsymmetryPolicy {
    private WindAsymmetryPolicy() {
    }

    public static double directionalProjection(
        TreeEnvironment environment,
        double directionX,
        double directionZ
    ) {
        if (environment == null) {
            throw new IllegalArgumentException("tree environment is required");
        }
        double directionLength = Math.hypot(directionX, directionZ);
        double windStrength = environment.windStrength();
        if (directionLength < 1.0e-9 || windStrength < 1.0e-9) {
            return 0.0;
        }
        double normalizedX = directionX / directionLength;
        double normalizedZ = directionZ / directionLength;
        double windX = environment.windX() / windStrength;
        double windZ = environment.windZ() / windStrength;
        return clamp(normalizedX * windX + normalizedZ * windZ, -1.0, 1.0);
    }

    public static double branchLengthMultiplier(
        TreeEnvironment environment,
        double directionX,
        double directionZ
    ) {
        double projection = directionalProjection(environment, directionX, directionZ);
        double response = responseStrength(environment);
        double multiplier = projection >= 0.0
            ? 1.0 + projection * response * 0.30
            : 1.0 + projection * response * 0.38;
        return clamp(multiplier, 0.68, 1.30);
    }

    public static double damageProbabilityBonus(
        TreeEnvironment environment,
        double directionX,
        double directionZ
    ) {
        double projection = directionalProjection(environment, directionX, directionZ);
        if (projection >= 0.0) {
            return 0.0;
        }
        double exposure = 0.45
            + environment.openSpaceStrength() * 0.35
            + environment.slope() * 0.20;
        return clamp(
            -projection * environment.windStrength() * exposure * 0.18,
            0.0,
            0.18
        );
    }

    public static double canopyDensityMultiplier(
        TreeEnvironment environment,
        double directionX,
        double directionZ
    ) {
        double projection = directionalProjection(environment, directionX, directionZ);
        double response = responseStrength(environment);
        double multiplier = projection >= 0.0
            ? 1.0 + projection * response * 0.20
            : 1.0 + projection * response * 0.30;
        return clamp(multiplier, 0.66, 1.20);
    }

    private static double responseStrength(TreeEnvironment environment) {
        double exposure = 0.52
            + environment.openSpaceStrength() * 0.30
            + environment.slope() * 0.18;
        return clamp(environment.windStrength() * exposure, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
