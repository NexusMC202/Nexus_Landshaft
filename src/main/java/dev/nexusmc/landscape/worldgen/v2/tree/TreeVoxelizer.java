package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Converts an immutable branch graph into a complete validated block model. */
public final class TreeVoxelizer {
    private TreeVoxelizer() {
    }

    /** Compatibility overload: legacy callers resolve the shared conifer as spruce. */
    public static VoxelTreeModel voxelize(
        long seed,
        TreeQualityTier quality,
        BranchGraph graph
    ) {
        return voxelize(graph, quality, seed, ConiferSpeciesProfile.SPRUCE);
    }

    /** Compatibility overload: legacy callers resolve the shared conifer as spruce. */
    public static VoxelTreeModel voxelize(
        BranchGraph graph,
        TreeQualityTier quality,
        long seed
    ) {
        return voxelize(graph, quality, seed, ConiferSpeciesProfile.SPRUCE);
    }

    public static VoxelTreeModel voxelize(
        long seed,
        TreeQualityTier quality,
        BranchGraph graph,
        ConiferSpeciesProfile species
    ) {
        return voxelize(graph, quality, seed, species);
    }

    public static VoxelTreeModel voxelize(
        BranchGraph graph,
        TreeQualityTier quality,
        long seed,
        ConiferSpeciesProfile species
    ) {
        if (graph == null || quality == null || species == null) {
            throw new IllegalArgumentException(
                "graph, quality and species are required"
            );
        }
        TreeEnvironment neutral = new TreeEnvironment(
            0.0, 0.65, 0.45, 0.0,
            0.0, 0.0, 0.0, 0.0, 96
        );
        AnatomyPlan anatomy = AnatomyPlan.resolve(
            seed, species, quality, neutral
        );
        return voxelize(graph, quality, anatomy.canopy(graph));
    }

    public static VoxelTreeModel voxelize(
        BranchGraph graph,
        TreeQualityTier quality,
        CanopyPlan canopy
    ) {
        if (graph == null || quality == null || canopy == null) {
            throw new IllegalArgumentException(
                "graph, quality and canopy are required"
            );
        }
        TreeQualityTier.TreeBudget budget = quality.budget();
        LinkedHashSet<VoxelTreeModel.Voxel> wood = new LinkedHashSet<>();
        LinkedHashSet<VoxelTreeModel.Voxel> leaves = new LinkedHashSet<>();

        for (BranchGraph.Segment segment : graph.segments()) {
            rasterizeWood(segment, wood, budget);
        }
        for (CanopyPlan.Cluster cluster : canopy.clusters()) {
            growCluster(cluster, leaves, wood, budget);
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

    private static void growCluster(
        CanopyPlan.Cluster cluster,
        Set<VoxelTreeModel.Voxel> leaves,
        Set<VoxelTreeModel.Voxel> wood,
        TreeQualityTier.TreeBudget budget
    ) {
        int horizontal = cluster.horizontalRadius();
        int verticalDown = cluster.verticalDown();
        int verticalUp = cluster.verticalUp();

        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -verticalDown; dy <= verticalUp; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    if (!acceptClusterVoxel(cluster, dx, dy, dz)) {
                        continue;
                    }
                    addLeaf(
                        cluster.centerX() + dx,
                        cluster.centerY() + dy,
                        cluster.centerZ() + dz,
                        leaves,
                        wood,
                        budget
                    );
                }
            }
        }
    }

    private static boolean acceptClusterVoxel(
        CanopyPlan.Cluster cluster,
        int dx,
        int dy,
        int dz
    ) {
        double horizontalShape =
            (dx * dx + dz * dz)
                / (double)(cluster.horizontalRadius() * cluster.horizontalRadius());
        double verticalScale = dy < 0
            ? cluster.verticalDown()
            : cluster.verticalUp();
        double verticalShape = (dy * dy) / (verticalScale * verticalScale);
        double shape = horizontalShape + verticalShape;
        long local = CanopyPlan.localHash(cluster.densitySeed(), dx, dy, dz);

        if (cluster.kind() == CanopyPlan.ClusterKind.SPRUCE_MASS) {
            if (shape > 1.22) {
                return false;
            }
            double edgeSkip = 0.22 / cluster.density();
            return shape <= 0.58 || CanopyPlan.unit(local) >= edgeSkip;
        }

        if (shape > 1.10) {
            return false;
        }
        double skip = shape > 0.48
            ? clamp(0.42 / cluster.density(), 0.22, 0.62)
            : clamp(0.16 / cluster.density(), 0.08, 0.30);
        return CanopyPlan.unit(local) >= skip;
    }

    private static void addLeaf(
        int x,
        int y,
        int z,
        Set<VoxelTreeModel.Voxel> leaves,
        Set<VoxelTreeModel.Voxel> wood,
        TreeQualityTier.TreeBudget budget
    ) {
        VoxelTreeModel.Voxel voxel = bounded(x, y, z, budget, false);
        if (voxel != null && !wood.contains(voxel)) {
            leaves.add(voxel);
        }
    }

    private static void addBounded(
        Set<VoxelTreeModel.Voxel> output,
        int x,
        int y,
        int z,
        TreeQualityTier.TreeBudget budget
    ) {
        VoxelTreeModel.Voxel voxel = bounded(x, y, z, budget, true);
        if (voxel != null) {
            output.add(voxel);
        }
    }

    private static VoxelTreeModel.Voxel bounded(
        int x,
        int y,
        int z,
        TreeQualityTier.TreeBudget budget,
        boolean wood
    ) {
        int minimumY = wood ? VoxelTreeModel.MIN_ROOT_Y : 0;
        if (Math.abs(x) > budget.maxHorizontalRadius()
            || Math.abs(z) > budget.maxHorizontalRadius()
            || y < minimumY || y > budget.maxHeight()) {
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
