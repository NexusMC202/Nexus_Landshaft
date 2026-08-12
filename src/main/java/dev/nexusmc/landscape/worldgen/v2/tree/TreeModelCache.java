package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.util.BoundedConcurrentCache;
import java.util.concurrent.atomic.DoubleAdder;
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
    private static final LongAdder CROWN_LEAVES = new LongAdder();
    private static final LongAdder LEEWARD_LEAVES = new LongAdder();
    private static final LongAdder WINDWARD_LEAVES = new LongAdder();
    private static final LongAdder NEUTRAL_LEAVES = new LongAdder();
    private static final DoubleAdder CROWN_X_WEIGHTED = new DoubleAdder();
    private static final DoubleAdder CROWN_Z_WEIGHTED = new DoubleAdder();
    private static final DoubleAdder WIND_PROJECTION_WEIGHTED = new DoubleAdder();

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
        BranchGraph baseGraph = SpeciesConiferBranchGenerator.generate(anatomy);
        WindDeformationPlan wind = WindDeformationPlan.resolve(anatomy);
        BranchGraph graph = wind.apply(baseGraph, anatomy);
        recordRoles(graph);
        CanopyPlan canopy = anatomy.canopy(graph);
        VoxelTreeModel generated = TreeVoxelizer.voxelize(
            graph,
            plan.quality(),
            canopy
        );
        recordCrown(CrownBiasMetrics.measure(generated, plan.environment()));
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

    private static void recordCrown(CrownBiasMetrics crown) {
        int leaves = crown.leafCount();
        CROWN_LEAVES.add(leaves);
        LEEWARD_LEAVES.add(crown.leewardLeaves());
        WINDWARD_LEAVES.add(crown.windwardLeaves());
        NEUTRAL_LEAVES.add(crown.neutralLeaves());
        CROWN_X_WEIGHTED.add(crown.centerX() * leaves);
        CROWN_Z_WEIGHTED.add(crown.centerZ() * leaves);
        WIND_PROJECTION_WEIGHTED.add(crown.windProjection() * leaves);
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

    public static CrownSnapshot crownSnapshot() {
        long leaves = CROWN_LEAVES.sum();
        double divisor = leaves == 0L ? 1.0 : leaves;
        return new CrownSnapshot(
            leaves,
            LEEWARD_LEAVES.sum(),
            WINDWARD_LEAVES.sum(),
            NEUTRAL_LEAVES.sum(),
            CROWN_X_WEIGHTED.sum() / divisor,
            CROWN_Z_WEIGHTED.sum() / divisor,
            WIND_PROJECTION_WEIGHTED.sum() / divisor
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
        CROWN_LEAVES.reset();
        LEEWARD_LEAVES.reset();
        WINDWARD_LEAVES.reset();
        NEUTRAL_LEAVES.reset();
        CROWN_X_WEIGHTED.reset();
        CROWN_Z_WEIGHTED.reset();
        WIND_PROJECTION_WEIGHTED.reset();
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

    public record CrownSnapshot(
        long leafCount,
        long leewardLeaves,
        long windwardLeaves,
        long neutralLeaves,
        double centerX,
        double centerZ,
        double windProjection
    ) {
        public CrownSnapshot {
            if (leafCount < 0L || leewardLeaves < 0L || windwardLeaves < 0L
                || neutralLeaves < 0L
                || leewardLeaves + windwardLeaves + neutralLeaves != leafCount
                || !Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(windProjection)) {
                throw new IllegalArgumentException("invalid crown snapshot");
            }
        }

        public long sideLeaves() {
            return leewardLeaves + windwardLeaves;
        }

        public double leewardShare() {
            long side = sideLeaves();
            return side == 0L ? 0.5 : leewardLeaves / (double)side;
        }

        public double directionalBalance() {
            long side = sideLeaves();
            return side == 0L ? 0.0
                : (leewardLeaves - windwardLeaves) / (double)side;
        }
    }
}
