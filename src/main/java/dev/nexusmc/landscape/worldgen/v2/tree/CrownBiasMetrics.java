package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Pure measurements of the voxelized crown relative to the local wind vector.
 * Positive wind projection means the foliage center of mass is displaced
 * leeward; negative values indicate windward bias.
 */
public record CrownBiasMetrics(
    int leafCount,
    double centerX,
    double centerZ,
    double windProjection,
    int leewardLeaves,
    int windwardLeaves,
    int neutralLeaves
) {
    private static final double SIDE_THRESHOLD = 0.25;

    public CrownBiasMetrics {
        if (leafCount < 0 || leewardLeaves < 0 || windwardLeaves < 0
            || neutralLeaves < 0
            || leewardLeaves + windwardLeaves + neutralLeaves != leafCount
            || !Double.isFinite(centerX) || !Double.isFinite(centerZ)
            || !Double.isFinite(windProjection)) {
            throw new IllegalArgumentException("invalid crown bias metrics");
        }
    }

    public static CrownBiasMetrics measure(
        VoxelTreeModel model,
        TreeEnvironment environment
    ) {
        if (model == null || environment == null) {
            throw new IllegalArgumentException("model and environment are required");
        }
        int count = model.leaves().size();
        if (count == 0) {
            return new CrownBiasMetrics(0, 0.0, 0.0, 0.0, 0, 0, 0);
        }

        double sumX = 0.0;
        double sumZ = 0.0;
        int leeward = 0;
        int windward = 0;
        int neutral = 0;

        double windStrength = environment.windStrength();
        double directionX = windStrength > 1.0e-9
            ? environment.windX() / windStrength
            : 0.0;
        double directionZ = windStrength > 1.0e-9
            ? environment.windZ() / windStrength
            : 0.0;

        for (VoxelTreeModel.Voxel leaf : model.leaves()) {
            sumX += leaf.x();
            sumZ += leaf.z();
            if (windStrength <= 1.0e-9) {
                neutral++;
                continue;
            }
            double projection = leaf.x() * directionX + leaf.z() * directionZ;
            if (projection > SIDE_THRESHOLD) {
                leeward++;
            } else if (projection < -SIDE_THRESHOLD) {
                windward++;
            } else {
                neutral++;
            }
        }

        double centerX = sumX / count;
        double centerZ = sumZ / count;
        double projection = centerX * directionX + centerZ * directionZ;
        return new CrownBiasMetrics(
            count,
            centerX,
            centerZ,
            projection,
            leeward,
            windward,
            neutral
        );
    }

    public int sideLeaves() {
        return leewardLeaves + windwardLeaves;
    }

    public double leewardShare() {
        int side = sideLeaves();
        return side == 0 ? 0.5 : leewardLeaves / (double)side;
    }

    public double directionalBalance() {
        int side = sideLeaves();
        return side == 0 ? 0.0 : (leewardLeaves - windwardLeaves) / (double)side;
    }
}
