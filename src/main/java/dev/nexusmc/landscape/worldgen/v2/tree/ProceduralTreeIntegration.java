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
        if (!ProceduralTreePolicy.supports(shape)) {
            return Result.UNSUPPORTED;
        }
        if (!ProceduralTreeRuntime.enabled()) {
            return Result.DISABLED;
        }
        if (level == null || base == null) {
            throw new IllegalArgumentException("level and base are required");
        }

        ProceduralTreePlan plan = ProceduralTreePlan.create(
            worldSeed,
            base.getX(),
            base.getZ(),
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
        return ProceduralTreeRuntime.placeConifer(level, base, plan)
            ? Result.PLACED
            : Result.COLLISION;
    }

    public enum Result {
        PLACED,
        COLLISION,
        DISABLED,
        UNSUPPORTED;

        public boolean shouldFallback() {
            return this != PLACED;
        }
    }
}
