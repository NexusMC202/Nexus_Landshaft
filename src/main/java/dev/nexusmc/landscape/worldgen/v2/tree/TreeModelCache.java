package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.util.BoundedConcurrentCache;
import java.util.concurrent.atomic.LongAdder;

/** Strictly bounded memoization for complete deterministic tree models. */
public final class TreeModelCache {
    public static final int DEFAULT_CAPACITY = 256;

    private static final BoundedConcurrentCache<Long, VoxelTreeModel> MODELS =
        new BoundedConcurrentCache<>(DEFAULT_CAPACITY);
    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder MISSES = new LongAdder();

    private TreeModelCache() {
    }

    public static VoxelTreeModel getOrCreate(ProceduralTreePlan plan) {
        return getOrCreateDetailed(plan).model();
    }

    public static Lookup getOrCreateDetailed(ProceduralTreePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("tree plan is required");
        }
        long key = plan.fingerprint();
        VoxelTreeModel cached = MODELS.get(key);
        if (cached != null) {
            HITS.increment();
            return new Lookup(cached, true);
        }
        MISSES.increment();

        AnatomyPlan anatomy = AnatomyPlan.resolve(plan);
        BranchGraph graph = SpeciesConiferBranchGenerator.generate(anatomy);
        CanopyPlan canopy = anatomy.canopy(graph);
        VoxelTreeModel generated = TreeVoxelizer.voxelize(
            graph,
            plan.quality(),
            canopy
        );
        MODELS.put(key, generated);
        VoxelTreeModel admitted = MODELS.get(key);
        return new Lookup(admitted != null ? admitted : generated, false);
    }

    public static Snapshot snapshot() {
        return new Snapshot(
            MODELS.size(),
            MODELS.capacity(),
            HITS.sum(),
            MISSES.sum()
        );
    }

    public static int size() {
        return MODELS.size();
    }

    public static int capacity() {
        return MODELS.capacity();
    }

    public static void clear() {
        MODELS.clear();
        HITS.reset();
        MISSES.reset();
    }

    public record Lookup(VoxelTreeModel model, boolean cacheHit) {
        public Lookup {
            if (model == null) {
                throw new IllegalArgumentException("tree model is required");
            }
        }
    }

    public record Snapshot(
        int size,
        int capacity,
        long hits,
        long misses
    ) {
        public Snapshot {
            if (size < 0 || capacity < 1 || size > capacity
                || hits < 0L || misses < 0L) {
                throw new IllegalArgumentException("invalid tree cache snapshot");
            }
        }

        public long requests() {
            return hits + misses;
        }

        public double hitRate() {
            long total = requests();
            return total == 0L ? 0.0 : hits / (double)total;
        }
    }
}
