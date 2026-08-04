package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Converts an immutable branch graph into a complete validated block model. */
public final class TreeVoxelizer {
    private TreeVoxelizer() {
    }

    /**
     * Compatibility overload for call sites that naturally start from seed.
     */
    public static VoxelTreeModel voxelize(
        long seed,
        TreeQualityTier quality,
        BranchGraph graph
    ) {
        return voxelize(graph, quality, seed);
    }

    public static VoxelTreeModel voxelize(
        BranchGraph graph,
        TreeQualityTier quality,
        long seed
    ) {
        if (graph == null || quality == null) {
            throw new IllegalArgumentException("graph and quality are required");
        }
        TreeQualityTier.TreeBudget budget = quality.budget();
        LinkedHashSet<VoxelTreeModel.Voxel> wood = new LinkedHashSet<>();
        LinkedHashSet<VoxelTreeModel.Voxel> leaves = new LinkedHashSet<>();

        for (BranchGraph.Segment segment : graph.segments()) {
            rasterizeWood(segment, wood, budget);
        }
        for (BranchGraph.Segment segment : graph.segments()) {
            if (!segment.dead() && segment.endRadius() <= 0.72) {
                growNeedleMass(segment, seed, leaves, wood, budget);
            }
        }

        return new VoxelTreeModel(
            quality,
            limited(wood, budget.maxWoodBlocks()),
            limited(leaves, budget.maxLeafBlocks())
        );
    }

    private static void rasterizeWood(
        BranchGraph.Segment segment,
        Set<VoxelTreeModel.Voxel> wood,
        TreeQualityTier.TreeBudget budget
    ) {
        int steps = Math.max(1, (int)Math.ceil(segment.length() * 2.5));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double)steps;
            double x = lerp(segment.startX(), segment.endX(), t);
            double y = lerp(segment.startY(), segment.endY(), t);
            double z = lerp(segment.startZ(), segment.endZ(), t);
            double radius = Math.max(0.38,
                lerp(segment.startRadius(), segment.endRadius(), t));
            fillSection(x, y, z, radius, wood, budget);
        }
    }

    private static void fillSection(
        double centerX,
        double centerY,
        double centerZ,
        double radius,
        Set<VoxelTreeModel.Voxel> output,
        TreeQualityTier.TreeBudget budget
    ) {
        int reach = Math.max(0, (int)Math.ceil(radius));
        int baseX = (int)Math.round(centerX);
        int baseY = (int)Math.round(centerY);
        int baseZ = (int)Math.round(centerZ);
        double threshold = radius * radius + 0.32;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dy * dy + dz * dz > threshold) {
                        continue;
                    }
                    addBounded(output, baseX + dx, baseY + dy, baseZ + dz, budget);
                }
            }
        }
    }

    private static void growNeedleMass(
        BranchGraph.Segment segment,
        long seed,
        Set<VoxelTreeModel.Voxel> leaves,
        Set<VoxelTreeModel.Voxel> wood,
        TreeQualityTier.TreeBudget budget
    ) {
        long hash = mix(seed ^ ((long)segment.id() * 0x9E3779B97F4A7C15L));
        int centerX = (int)Math.round(segment.endX());
        int centerY = (int)Math.round(segment.endY());
        int centerZ = (int)Math.round(segment.endZ());
        int horizontal = 1 + (unit(hash) > 0.30 ? 1 : 0);
        int vertical = 1 + (unit(mix(hash)) > 0.64 ? 1 : 0);

        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    double shape = (dx * dx + dz * dz) / (double)(horizontal * horizontal)
                        + (dy * dy) / (double)(vertical * vertical);
                    if (shape > 1.18) {
                        continue;
                    }
                    long local = mix(hash
                        ^ ((long)dx * 0x632BE59BD9B4E019L)
                        ^ ((long)dy * 0x94D049BB133111EBL)
                        ^ ((long)dz * 0xC2B2AE3D27D4EB4FL));
                    if (shape > 0.62 && unit(local) < 0.24) {
                        continue;
                    }
                    VoxelTreeModel.Voxel voxel = bounded(
                        centerX + dx, centerY + dy, centerZ + dz, budget
                    );
                    if (voxel != null && !wood.contains(voxel)) {
                        leaves.add(voxel);
                    }
                }
            }
        }
    }

    private static void addBounded(
        Set<VoxelTreeModel.Voxel> output,
        int x,
        int y,
        int z,
        TreeQualityTier.TreeBudget budget
    ) {
        VoxelTreeModel.Voxel voxel = bounded(x, y, z, budget);
        if (voxel != null) {
            output.add(voxel);
        }
    }

    private static VoxelTreeModel.Voxel bounded(
        int x,
        int y,
        int z,
        TreeQualityTier.TreeBudget budget
    ) {
        if (Math.abs(x) > budget.maxHorizontalRadius()
            || Math.abs(z) > budget.maxHorizontalRadius()
            || y < 0 || y > budget.maxHeight()) {
            return null;
        }
        return new VoxelTreeModel.Voxel(x, y, z);
    }

    private static List<VoxelTreeModel.Voxel> limited(
        Set<VoxelTreeModel.Voxel> source,
        int limit
    ) {
        List<VoxelTreeModel.Voxel> result = new ArrayList<>(Math.min(source.size(), limit));
        for (VoxelTreeModel.Voxel voxel : source) {
            if (result.size() == limit) {
                break;
            }
            result.add(voxel);
        }
        return List.copyOf(result);
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
