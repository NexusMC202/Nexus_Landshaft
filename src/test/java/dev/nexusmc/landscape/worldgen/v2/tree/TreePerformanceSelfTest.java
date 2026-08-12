package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Performance contract for the procedural tree pipeline. Wall-clock timings are
 * printed for diagnostics only; CI assertions use deterministic cache/work
 * invariants so runner speed cannot make the test flaky.
 */
public final class TreePerformanceSelfTest {
    private static final int REPEATS_PER_PLAN = 5;

    private TreePerformanceSelfTest() {
    }

    public static void main(String[] args) {
        TreeModelCache.clear();
        int plans = 0;
        long totalVoxels = 0L;
        long coldNanos = 0L;
        long hitNanos = 0L;

        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                ProceduralTreePlan plan = plan(species, quality, plans);

                long coldStarted = System.nanoTime();
                TreeModelCache.Lookup cold = TreeModelCache.getOrCreateDetailed(plan);
                coldNanos += System.nanoTime() - coldStarted;
                require(!cold.cacheHit(), "first lookup must be a cache miss");
                require(!cold.model().wood().isEmpty(), "generated tree has no wood");
                require(!cold.model().leaves().isEmpty(), "generated tree has no leaves");
                totalVoxels += cold.model().wood().size() + cold.model().leaves().size();

                for (int repeat = 0; repeat < REPEATS_PER_PLAN; repeat++) {
                    long hitStarted = System.nanoTime();
                    TreeModelCache.Lookup repeated =
                        TreeModelCache.getOrCreateDetailed(plan);
                    hitNanos += System.nanoTime() - hitStarted;
                    require(repeated.cacheHit(), "repeated lookup must hit cache");
                    require(repeated.model() == cold.model(),
                        "cache hit must reuse the admitted model instance");
                }
                plans++;
            }
        }

        TreeModelCache.Snapshot snapshot = TreeModelCache.snapshot();
        long expectedHits = (long)plans * REPEATS_PER_PLAN;
        require(snapshot.misses() == plans,
            "heavy generation count must equal unique plans");
        require(snapshot.hits() == expectedHits,
            "cache hit count mismatch");
        require(snapshot.requests() == plans + expectedHits,
            "cache request count mismatch");
        require(snapshot.size() == plans,
            "cache must contain one model per unique plan");
        require(snapshot.size() <= snapshot.capacity(),
            "cache capacity exceeded");
        require(snapshot.hitRate() > 0.80,
            "repeat workload must achieve a high cache hit rate");

        double coldMicros = coldNanos / 1_000.0 / plans;
        double hitMicros = hitNanos / 1_000.0 / expectedHits;
        System.out.println("TreePerformanceSelfTest: PASS");
        System.out.println("plans=" + plans
            + " voxels=" + totalVoxels
            + " misses=" + snapshot.misses()
            + " hits=" + snapshot.hits()
            + " hitRate=" + snapshot.hitRate());
        System.out.println("diagnostic_avg_cold_us=" + coldMicros
            + " diagnostic_avg_hit_us=" + hitMicros);
    }

    private static ProceduralTreePlan plan(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        int index
    ) {
        long seed = 0x504552465F545245L + index * 0x9E3779B97F4A7C15L;
        TreeEnvironment environment = new TreeEnvironment(
            0.12 + index * 0.01,
            0.62,
            0.38,
            0.06,
            -0.24,
            0.10,
            0.58,
            -0.06,
            96 + index
        );
        return new ProceduralTreePlan(
            seed,
            species,
            quality,
            environment,
            quality == TreeQualityTier.HERO
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
