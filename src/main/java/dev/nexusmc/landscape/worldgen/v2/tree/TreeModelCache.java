package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.util.BoundedConcurrentCache;

/** Strictly bounded memoization for complete deterministic tree models. */
public final class TreeModelCache {
    public static final int DEFAULT_CAPACITY = 256;

    private static final BoundedConcurrentCache<Long, VoxelTreeModel> MODELS =
        new BoundedConcurrentCache<>(DEFAULT_CAPACITY);

    private TreeModelCache() {
    }

    public static VoxelTreeModel getOrCreate(ProceduralTreePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("tree plan is required");
        }
        long key = plan.fingerprint();
        VoxelTreeModel cached = MODELS.get(key);
        if (cached != null) {
            return cached;
        }

        TreeLifeHistory history = TreeLifeHistory.generate(
            plan.seed(), plan.quality(), plan.environment()
        );
        BranchGraph graph = SpeciesConiferBranchGenerator.generate(
            plan.seed(),
            plan.species(),
            plan.quality(),
            plan.environment(),
            history
        );
        VoxelTreeModel generated = TreeVoxelizer.voxelize(
            graph,
            plan.quality(),
            plan.seed(),
            plan.species()
        );
        MODELS.put(key, generated);
        VoxelTreeModel admitted = MODELS.get(key);
        return admitted != null ? admitted : generated;
    }

    public static int size() {
        return MODELS.size();
    }

    public static int capacity() {
        return MODELS.capacity();
    }

    public static void clear() {
        MODELS.clear();
    }
}
