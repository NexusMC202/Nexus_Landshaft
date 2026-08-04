package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceNoise;

/**
 * Immutable decision produced before runtime placement. The plan is pure data,
 * so worldgen can log, compare and reproduce any generated tree exactly.
 */
public record ProceduralTreePlan(
    long seed,
    TreeQualityTier quality,
    TreeEnvironment environment,
    boolean oldGrowth
) {
    private static final long TREE_SALT = 0x545245455F56324CL;

    public ProceduralTreePlan {
        if (quality == null || environment == null) {
            throw new IllegalArgumentException("tree plan requires quality and environment");
        }
    }

    public static ProceduralTreePlan create(
        long worldSeed,
        int blockX,
        int blockZ,
        boolean oldGrowth,
        double oldGrowthDensity,
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
        TreeEnvironment environment = ProceduralTreePolicy.environment(
            slope,
            soilMoisture,
            forestCompetition,
            riverInfluence,
            windX,
            windZ,
            openSpaceX,
            openSpaceZ,
            surfaceY
        );
        TreeQualityTier quality = ProceduralTreePolicy.quality(
            oldGrowth,
            oldGrowthDensity,
            environment
        );
        long seed = SurfaceNoise.hash(worldSeed, blockX, blockZ, TREE_SALT);
        return new ProceduralTreePlan(seed, quality, environment, oldGrowth);
    }

    public long fingerprint() {
        long hash = seed ^ ((long)quality.ordinal() << 59);
        hash = mix(hash, Double.doubleToLongBits(environment.slope()));
        hash = mix(hash, Double.doubleToLongBits(environment.soilMoisture()));
        hash = mix(hash, Double.doubleToLongBits(environment.forestCompetition()));
        hash = mix(hash, Double.doubleToLongBits(environment.riverInfluence()));
        hash = mix(hash, Double.doubleToLongBits(environment.windX()));
        hash = mix(hash, Double.doubleToLongBits(environment.windZ()));
        hash = mix(hash, Double.doubleToLongBits(environment.openSpaceX()));
        hash = mix(hash, Double.doubleToLongBits(environment.openSpaceZ()));
        hash = mix(hash, environment.surfaceY());
        return mix(hash, oldGrowth ? 1L : 0L);
    }

    private static long mix(long hash, long value) {
        hash ^= value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        return hash;
    }
}
