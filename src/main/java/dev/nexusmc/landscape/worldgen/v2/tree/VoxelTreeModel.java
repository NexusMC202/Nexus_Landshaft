package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete block model validated before any world mutation. */
public record VoxelTreeModel(
    TreeQualityTier quality,
    List<Voxel> wood,
    List<Voxel> leaves
) {
    public static final int MIN_ROOT_Y = -2;
    public static final Voxel ORIGIN_WOOD = new Voxel(0, 0, 0);

    public VoxelTreeModel {
        if (quality == null) {
            throw new IllegalArgumentException("quality is null");
        }
        wood = List.copyOf(wood);
        leaves = List.copyOf(leaves);
        if (wood.isEmpty()) {
            throw new IllegalArgumentException("tree has no wood");
        }
        if (!wood.contains(ORIGIN_WOOD)) {
            throw new IllegalArgumentException("tree has no trunk origin");
        }
        validate(quality.budget(), wood, leaves);
    }

    private static void validate(
        TreeQualityTier.TreeBudget budget,
        List<Voxel> wood,
        List<Voxel> leaves
    ) {
        if (wood.size() > budget.maxWoodBlocks()) {
            throw new IllegalArgumentException("wood budget exceeded");
        }
        if (leaves.size() > budget.maxLeafBlocks()) {
            throw new IllegalArgumentException("leaf budget exceeded");
        }
        Set<Voxel> occupied = new HashSet<>();
        for (Voxel voxel : wood) {
            validateBounds(voxel, budget, true);
            if (!occupied.add(voxel)) {
                throw new IllegalArgumentException("duplicate wood voxel: " + voxel);
            }
        }
        for (Voxel voxel : leaves) {
            validateBounds(voxel, budget, false);
            if (!occupied.add(voxel)) {
                throw new IllegalArgumentException("overlapping voxel: " + voxel);
            }
        }
    }

    private static void validateBounds(
        Voxel voxel,
        TreeQualityTier.TreeBudget budget,
        boolean wood
    ) {
        if (Math.abs(voxel.x()) > budget.maxHorizontalRadius()
            || Math.abs(voxel.z()) > budget.maxHorizontalRadius()) {
            throw new IllegalArgumentException("horizontal tree budget exceeded");
        }
        int minimumY = wood ? MIN_ROOT_Y : 0;
        if (voxel.y() < minimumY || voxel.y() > budget.maxHeight()) {
            throw new IllegalArgumentException("vertical tree budget exceeded");
        }
    }

    public int totalBlocks() {
        return Math.addExact(wood.size(), leaves.size());
    }

    public long fingerprint() {
        long hash = quality.ordinal() + 0x9E3779B97F4A7C15L;
        for (Voxel voxel : wood) {
            hash = mix(hash, voxel, 0x57L);
        }
        for (Voxel voxel : leaves) {
            hash = mix(hash, voxel, 0x1EAFL);
        }
        return hash;
    }

    private static long mix(long hash, Voxel voxel, long salt) {
        long value = ((long)voxel.x() * 0x632BE59BD9B4E019L)
            ^ ((long)voxel.y() * 0x9E3779B185EBCA87L)
            ^ ((long)voxel.z() * 0xC2B2AE3D27D4EB4FL)
            ^ salt;
        hash ^= value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        return hash;
    }

    public record Voxel(int x, int y, int z) {
    }
}
