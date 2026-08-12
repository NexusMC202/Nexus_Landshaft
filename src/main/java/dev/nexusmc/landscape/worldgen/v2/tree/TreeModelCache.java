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
    private static final LongAdder TRUNK_SEGMENTS = new LongAdder();
    private static final LongAdder ROOT_SEGMENTS = new LongAdder();
    private static final LongAdder LIVE_BRANCH_SEGMENTS = new LongAdder();
    private static final LongAdder DEAD_BRANCH_SEGMENTS = new LongAdder();
    private static final LongAdder SECONDARY_LEADER_SEGMENTS = new LongAdder();

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
        recordRoles(graph);
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

    private static void recordRoles(BranchGraph graph) {
        for (BranchGraph.Segment segment : graph.segments()) {
            switch (segment.role()) {
                case TRUNK -> TRUNK_SEGMENTS.increment();
                case ROOT -> ROOT_SEGMENTS.increment();
                case LIVE_BRANCH -> LIVE_BRANCH_SEGMENTS.increment();
                case DEAD_BRANCH -> DEAD_BRANCH_SEGMENTS.increment();
                case SECONDARY_LEADER -> SECONDARY_LEADER_SEGMENTS.increment();
            }
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(
            MODELS.size(),
            MODELS.capacity(),
            HITS.sum(),
            MISSES.sum()
        );
    }

    public static StructureSnapshot structureSnapshot() {
        return new StructureSnapshot(
            TRUNK_SEGMENTS.sum(),
            ROOT_SEGMENTS.sum(),
            LIVE_BRANCH_SEGMENTS.sum(),
            DEAD_BRANCH_SEGMENTS.sum(),
            SECONDARY_LEADER_SEGMENTS.sum()
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
        TRUNK_SEGMENTS.reset();
        ROOT_SEGMENTS.reset();
        LIVE_BRANCH_SEGMENTS.reset();
        DEAD_BRANCH_SEGMENTS.reset();
        SECONDARY_LEADER_SEGMENTS.reset();
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

    public record StructureSnapshot(
        long trunkSegments,
        long rootSegments,
        long liveBranchSegments,
        long deadBranchSegments,
        long secondaryLeaderSegments
    ) {
        public StructureSnapshot {
            if (trunkSegments < 0L || rootSegments < 0L
                || liveBranchSegments < 0L || deadBranchSegments < 0L
                || secondaryLeaderSegments < 0L) {
                throw new IllegalArgumentException("invalid tree structure snapshot");
            }
        }

        public long totalSegments() {
            return trunkSegments + rootSegments + liveBranchSegments
                + deadBranchSegments + secondaryLeaderSegments;
        }
    }
}
