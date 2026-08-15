package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;

/**
 * Centralizes the rollout policy for Tree System v2. Keeping this logic out
 * of the vegetation pass prevents runtime integration from accumulating
 * magic thresholds and species-specific branching.
 */
public final class ProceduralTreePolicy {
    private ProceduralTreePolicy() {
    }

    public static boolean supports(VegetationProfile.TreeShape shape) {
        return shape != null
            && shape != VegetationProfile.TreeShape.GIANT_MUSHROOM;
    }

    public static TreeQualityTier quality(
        boolean oldGrowth,
        double oldGrowthDensity,
        TreeEnvironment environment
    ) {
        if (environment == null) {
            throw new IllegalArgumentException("environment is required");
        }
        if (!Double.isFinite(oldGrowthDensity)
            || oldGrowthDensity < 0.0
            || oldGrowthDensity > 1.0) {
            throw new IllegalArgumentException(
                "oldGrowthDensity outside [0,1]: " + oldGrowthDensity
            );
        }

        double exposure = environment.slope() * 0.42
            + environment.windStrength() * 0.38
            + (1.0 - environment.forestCompetition()) * 0.20;
        double maturity = oldGrowthDensity * 0.55
            + environment.soilMoisture() * 0.18
            + environment.openSpaceStrength() * 0.12
            + (oldGrowth ? 0.35 : 0.0);

        if (oldGrowth && maturity >= 0.72 && exposure < 0.82) {
            return TreeQualityTier.HERO;
        }
        if (oldGrowth || maturity >= 0.44 || exposure >= 0.48) {
            return TreeQualityTier.MID;
        }
        return TreeQualityTier.BASIC;
    }

    public static TreeEnvironment environment(
        double slope,
        double soilMoisture,
        double forestCompetition,
        double riverInfluence,
        double windX,
        double windZ,
        double openSpaceX,
        double openSpaceZ,
        int surfaceY
    ) {
        return new TreeEnvironment(
            clamp01(slope),
            clamp01(soilMoisture),
            clamp01(forestCompetition),
            clamp01(riverInfluence),
            clampSigned(windX),
            clampSigned(windZ),
            clampSigned(openSpaceX),
            clampSigned(openSpaceZ),
            surfaceY
        );
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite tree policy input");
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampSigned(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite tree policy input");
        }
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
