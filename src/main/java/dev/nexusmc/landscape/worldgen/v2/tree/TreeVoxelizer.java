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
        TreeQualityTier.TreeBudget budget = quality.budget();
        LinkedHashSet<VoxelTreeModel.Voxel> wood = new LinkedHashSet<>();
        LinkedHashSet<VoxelTreeModel.Voxel> leaves = new LinkedHashSet<>();

        for (BranchGraph.Segment segment : graph.segments()) {
            rasterizeWood(segment, wood, budget);
        }

        double crownTop = crownTop(graph);
        for (BranchGraph.Segment segment : graph.segments()) {
            if (segment.dead() || segment.endRadius() > 0.72) {
                continue;
            }
            if (species == ConiferSpeciesProfile.PINE) {
                growPineNeedleCluster(
                    segment, crownTop, seed, leaves, wood, budget, species
                );
            } else {
                growSpruceNeedleMass(
                    segment, crownTop, seed, leaves, wood, budget, species
                );
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

    private static void growSpruceNeedleMass(
        BranchGraph.Segment segment,
        double crownTop,
        long seed,
        Set<VoxelTreeModel.Voxel> leaves,
        Set<VoxelTreeModel.Voxel> wood,
        TreeQualityTier.TreeBudget budget,
        ConiferSpeciesProfile species
    ) {
        long hash = foliageHash(seed, segment.id(), species);
        double relativeHeight = crownTop <= 0.0
            ? 1.0
            : clamp(segment.endY() / crownTop, 0.0, 1.0);
        int centerX = (int)Math.round(segment.endX());
        int centerY = (int)Math.round(segment.endY() - (1.0 - relativeHeight) * 0.45);
        int centerZ = (int)Math.round(segment.endZ());

        double density = species.foliageDensityMultiplier();
        int horizontal = 1 + (unit(hash) < 0.68 * density ? 1 : 0);
        int verticalDown = 1 + (relativeHeight < 0.72 ? 1 : 0);
        int verticalUp = relativeHeight > 0.80 ? 2 : 1;

        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -verticalDown; dy <= verticalUp; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    double horizontalShape =
                        (dx * dx + dz * dz) / (double)(horizontal * horizontal);
                    double verticalScale = dy < 0 ? verticalDown : verticalUp;
                    double verticalShape = (dy * dy) / (verticalScale * verticalScale);
                    double shape = horizontalShape + verticalShape;
                    if (shape > 1.22) {
                        continue;
                    }
                    long local = localHash(hash, dx, dy, dz);
                    double edgeSkip = 0.22 / density;
                    if (shape > 0.58 && unit(local) < edgeSkip) {
                        continue;
                    }
                    addLeaf(
                        centerX + dx,
                        centerY + dy,
                        centerZ + dz,
                        leaves,
                        wood,
                        budget
                    );
                }
            }
        }
    }

    private static void growPineNeedleCluster(
        BranchGraph.Segment segment,
        double crownTop,
        long seed,
        Set<VoxelTreeModel.Voxel> leaves,
        Set<VoxelTreeModel.Voxel> wood,
        TreeQualityTier.TreeBudget budget,
        ConiferSpeciesProfile species
    ) {
        double relativeHeight = crownTop <= 0.0
            ? 1.0
            : clamp(segment.endY() / crownTop, 0.0, 1.0);
        if (relativeHeight < 0.54) {
            return;
        }

        long hash = foliageHash(seed, segment.id(), species);
        double acceptance = 0.52
            + (relativeHeight - 0.54) * 0.82
            * species.upperCrownMultiplier();
        if (relativeHeight < 0.74 && unit(hash) > acceptance) {
            return;
        }

        int centerX = (int)Math.round(segment.endX());
        int centerY = (int)Math.round(segment.endY());
        int centerZ = (int)Math.round(segment.endZ());
        int horizontal = relativeHeight > 0.76 ? 2 : 1;
        if (unit(mix(hash)) > 0.72) {
            horizontal++;
        }
        horizontal = Math.min(horizontal, 3);
        int vertical = relativeHeight > 0.84 ? 2 : 1;
        double density = species.foliageDensityMultiplier();

        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    double shape =
                        (dx * dx + dz * dz) / (double)(horizontal * horizontal)
                            + (dy * dy) / (double)(vertical * vertical);
                    if (shape > 1.10) {
                        continue;
                    }
                    long local = localHash(hash, dx, dy, dz);
                    double skip = shape > 0.48
                        ? clamp(0.42 / density, 0.22, 0.62)
                        : clamp(0.16 / density, 0.08, 0.30);
                    if (unit(local) < skip) {
                        continue;
                    }
                    addLeaf(
                        centerX + dx,
                        centerY + dy,
                        centerZ + dz,
                        leaves,
                        wood,
                        budget
                    );
                }
            }
        }
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

    private static double crownTop(BranchGraph graph) {
        double top = 0.0;
        for (BranchGraph.Segment segment : graph.segments()) {
            if (!segment.dead()) {
                top = Math.max(top, Math.max(segment.startY(), segment.endY()));
            }
        }
        return top;
    }

    private static long foliageHash(
        long seed,
        int segmentId,
        ConiferSpeciesProfile species
    ) {
        return mix(seed
            ^ ((long)segmentId * 0x9E3779B97F4A7C15L)
            ^ ((long)species.ordinal() * 0xD1B54A32D192ED03L));
    }

    private static long localHash(long hash, int dx, int dy, int dz) {
        return mix(hash
            ^ ((long)dx * 0x632BE59BD9B4E019L)
            ^ ((long)dy * 0x94D049BB133111EBL)
            ^ ((long)dz * 0xC2B2AE3D27D4EB4FL));
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

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
