package dev.nexusmc.landscape.worldgen.v2.tree;

/** Deterministic geometric measurements of a voxel foliage crown. */
public record CrownShapeMetrics(
    int leafCount,
    int minY,
    int maxY,
    int spanX,
    int spanZ,
    double meanY,
    double lowerCrownShare,
    double upperCrownShare
) {
    public CrownShapeMetrics {
        if (leafCount <= 0 || minY > maxY || spanX < 0 || spanZ < 0
            || !Double.isFinite(meanY)
            || !validShare(lowerCrownShare)
            || !validShare(upperCrownShare)) {
            throw new IllegalArgumentException("invalid crown shape metrics");
        }
    }

    public static CrownShapeMetrics measure(VoxelTreeModel model) {
        if (model == null || model.leaves().isEmpty()) {
            throw new IllegalArgumentException("foliage model is required");
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        long sumY = 0L;

        for (VoxelTreeModel.Voxel leaf : model.leaves()) {
            minX = Math.min(minX, leaf.x());
            maxX = Math.max(maxX, leaf.x());
            minY = Math.min(minY, leaf.y());
            maxY = Math.max(maxY, leaf.y());
            minZ = Math.min(minZ, leaf.z());
            maxZ = Math.max(maxZ, leaf.z());
            sumY += leaf.y();
        }

        double height = Math.max(1.0, maxY - minY);
        double lowerCut = minY + height * 0.45;
        double upperCut = minY + height * 0.70;
        int lower = 0;
        int upper = 0;
        for (VoxelTreeModel.Voxel leaf : model.leaves()) {
            if (leaf.y() <= lowerCut) {
                lower++;
            }
            if (leaf.y() >= upperCut) {
                upper++;
            }
        }

        int leaves = model.leaves().size();
        return new CrownShapeMetrics(
            leaves,
            minY,
            maxY,
            maxX - minX,
            maxZ - minZ,
            sumY / (double)leaves,
            lower / (double)leaves,
            upper / (double)leaves
        );
    }

    public int height() {
        return maxY - minY + 1;
    }

    public double horizontalSpan() {
        return (spanX + spanZ) * 0.5;
    }

    private static boolean validShare(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
