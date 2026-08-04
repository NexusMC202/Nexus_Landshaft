package dev.nexusmc.landscape.worldgen.v2.vegetation;

import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContext;

/**
 * Pure vegetation grammar. It never places blocks and can be shared by tests,
 * runtime decoration and diagnostics.
 */
public final class VegetationResolver {
    private VegetationResolver() {
    }

    public static VegetationSelection resolve(
        VegetationProfile profile,
        VegetationContext context
    ) {
        SurfaceContext surface = context.surface();
        boolean terrestrial = profile.terrestrial()
            && !context.underground()
            && !context.waterAtSurface();
        double slopeFactor = 1.0 - smoothstep(
            profile.maxTreeSlope() * 0.55,
            profile.maxTreeSlope(),
            surface.slope()
        );
        double heightFactor = 1.0 - smoothstep(
            profile.treeLineY() - 28.0,
            profile.treeLineY() + 8.0,
            surface.surfaceY()
        );
        double riverPresence = Math.max(
            surface.riverMask(),
            surface.riverInfluence()
        );
        double channelClearance = 1.0 - smoothstep(
            0.34,
            0.68,
            riverPresence
        );
        double riparianBoost = surface.wetBank()
            && riverPresence < 0.34
            ? 1.38
            : 1.0;
        double lowlandBoost = 0.82
            + (1.0 - surface.normalizedHeight()) * 0.34;
        double forestCluster = 0.16
            + context.forestCore() * context.forestCore() * 0.84;
        double clearingFactor = context.clearingNoise() < profile.clearingShare()
            ? 0.06
            : 1.0;
        double wetBoost = 0.72 + surface.groundwater() * 0.45;
        double tree = (terrestrial ? profile.treeDensity() : 0.0)
            * forestCluster
            * slopeFactor
            * heightFactor
            * channelClearance
            * riparianBoost
            * lowlandBoost
            * clearingFactor;
        boolean treesAllowed = terrestrial
            && !profile.treeShapes().isEmpty()
            && tree > 0.002;
        return new VegetationSelection(
            profile.profileId(),
            clamp01(tree),
            terrestrial ? clamp01(profile.shrubDensity() * wetBoost * slopeFactor) : 0.0,
            terrestrial ? clamp01(profile.groundDensity() * wetBoost) : 0.0,
            terrestrial ? clamp01(profile.flowerDensity() * wetBoost) : 0.0,
            terrestrial ? profile.deadwoodDensity() * channelClearance : 0.0,
            terrestrial ? clamp01(profile.rockDensity() * (0.45 + surface.slope())) : 0.0,
            treesAllowed ? profile.oldGrowthDensity() * heightFactor : 0.0,
            treesAllowed,
            terrestrial
        );
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
