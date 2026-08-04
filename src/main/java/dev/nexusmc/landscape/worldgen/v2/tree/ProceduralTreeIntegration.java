package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Thin runtime integration boundary used by the vegetation pass. All
 * procedural-tree decisions live here so the worldgen pass only supplies
 * already-sampled environmental values.
 */
public final class ProceduralTreeIntegration {
    private ProceduralTreeIntegration() {
    }

    public static Result tryPlace(
        WorldGenLevel level,
        BlockPos base,
        long worldSeed,
        VegetationProfile.TreeShape shape,
        boolean oldGrowth,
        double oldGrowthDensity,
        double slope,
        double soilMoisture,
        double forestCompetition,
        double riverInfluence,
        double windX,
        double windZ,
        double openSpaceX,
        double openSpaceZ
    ) {
        return tryPlaceDetailed(
            level,
            base,
            worldSeed,
            shape,
            oldGrowth,
            oldGrowthDensity,
            slope,
            soilMoisture,
            forestCompetition,
            riverInfluence,
            windX,
            windZ,
            openSpaceX,
            openSpaceZ
        ).result();
    }

    public static Outcome tryPlaceDetailed(
        WorldGenLevel level,
        BlockPos base,
        long worldSeed,
        VegetationProfile.TreeShape shape,
        boolean oldGrowth,
        double oldGrowthDensity,
        double slope,
        double soilMoisture,
        double forestCompetition,
        double riverInfluence,
        double windX,
        double windZ,
        double openSpaceX,
        double openSpaceZ
    ) {
        if (!ProceduralTreePolicy.supports(shape)) {
            return new Outcome(Result.UNSUPPORTED, null, 0L);
        }
        if (!ProceduralTreeRuntime.enabled()) {
            return new Outcome(Result.DISABLED, null, 0L);
        }
        if (level == null || base == null) {
            throw new IllegalArgumentException("level and base are required");
        }

        ProceduralTreePlan plan = ProceduralTreePlan.create(
            worldSeed,
            base.getX(),
            base.getZ(),
            shape,
            oldGrowth,
            oldGrowthDensity,
            slope,
            soilMoisture,
            forestCompetition,
            riverInfluence,
            windX,
            windZ,
            openSpaceX,
            openSpaceZ,
            base.getY() - 1
        );
        if (!TreeRegionalQuotaPolicy.allows(
            worldSeed,
            base.getX(),
            base.getZ(),
            plan.quality()
        )) {
            return new Outcome(
                Result.QUOTA_REJECTED,
                plan.quality(),
                plan.fingerprint()
            );
        }

        Result result = ProceduralTreeRuntime.placeConifer(level, base, plan)
            ? Result.PLACED
            : Result.COLLISION;
        return new Outcome(result, plan.quality(), plan.fingerprint());
    }

    public record Outcome(
        Result result,
        TreeQualityTier quality,
        long planFingerprint
    ) {
        public Outcome {
            if (result == null) {
                throw new IllegalArgumentException("result is required");
            }
            if ((result == Result.PLACED
                || result == Result.COLLISION
                || result == Result.QUOTA_REJECTED)
                && quality == null) {
                throw new IllegalArgumentException(
                    "runtime outcomes require a quality tier"
                );
            }
        }

        public boolean shouldFallback() {
            return result.shouldFallback();
        }
    }

    public enum Result {
        PLACED,
        COLLISION,
        QUOTA_REJECTED,
        DISABLED,
        UNSUPPORTED;

        public boolean shouldFallback() {
            return this != PLACED && this != QUOTA_REJECTED;
        }
    }
}
