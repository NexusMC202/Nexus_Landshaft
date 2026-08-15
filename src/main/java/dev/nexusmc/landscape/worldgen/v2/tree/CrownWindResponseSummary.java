package dev.nexusmc.landscape.worldgen.v2.tree;

/** Aggregate crown morphology across a deterministic seed sample. */
public record CrownWindResponseSummary(
    int samples,
    long leafCount,
    long leewardLeaves,
    long windwardLeaves,
    long neutralLeaves,
    double meanWindProjection,
    double meanDirectionalBalance
) {
    public CrownWindResponseSummary {
        if (samples <= 0 || leafCount < 0L || leewardLeaves < 0L
            || windwardLeaves < 0L || neutralLeaves < 0L
            || leewardLeaves + windwardLeaves + neutralLeaves != leafCount
            || !Double.isFinite(meanWindProjection)
            || !Double.isFinite(meanDirectionalBalance)) {
            throw new IllegalArgumentException("invalid crown wind response summary");
        }
    }

    public static CrownWindResponseSummary measure(
        long rootSeed,
        int samples,
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        if (samples <= 0 || species == null || quality == null || environment == null) {
            throw new IllegalArgumentException("aggregate crown inputs are required");
        }

        long leaves = 0L;
        long leeward = 0L;
        long windward = 0L;
        long neutral = 0L;
        double projectionSum = 0.0;
        double balanceSum = 0.0;

        for (int index = 0; index < samples; index++) {
            long seed = TreeLifeHistory.mix(
                rootSeed
                    ^ ((long)species.ordinal() << 56)
                    ^ ((long)quality.ordinal() << 48)
                    ^ index * 0x9E3779B97F4A7C15L
            );
            ProceduralTreePlan plan = new ProceduralTreePlan(
                seed,
                species,
                quality,
                environment,
                quality == TreeQualityTier.HERO
            );
            VoxelTreeModel model = TreeGenerationPipeline.generate(plan).model();
            CrownBiasMetrics metrics = CrownBiasMetrics.measure(model, environment);
            leaves += metrics.leafCount();
            leeward += metrics.leewardLeaves();
            windward += metrics.windwardLeaves();
            neutral += metrics.neutralLeaves();
            projectionSum += metrics.windProjection();
            balanceSum += metrics.directionalBalance();
        }

        return new CrownWindResponseSummary(
            samples,
            leaves,
            leeward,
            windward,
            neutral,
            projectionSum / samples,
            balanceSum / samples
        );
    }

    public long sideLeaves() {
        return leewardLeaves + windwardLeaves;
    }

    public double leewardShare() {
        long side = sideLeaves();
        return side == 0L ? 0.5 : leewardLeaves / (double)side;
    }
}
