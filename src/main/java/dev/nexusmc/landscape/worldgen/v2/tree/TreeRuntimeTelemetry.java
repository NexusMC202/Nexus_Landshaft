package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free aggregate telemetry for the procedural tree runtime. Counters are
 * independent from vegetation-pass lifecycle so cache and quota behavior can
 * be measured without coupling the tree engine to one worldgen caller.
 */
public final class TreeRuntimeTelemetry {
    private static final LongAdder ATTEMPTS = new LongAdder();
    private static final LongAdder PLACED = new LongAdder();
    private static final LongAdder COLLISIONS = new LongAdder();
    private static final LongAdder QUOTA_REJECTED = new LongAdder();
    private static final LongAdder DISABLED = new LongAdder();
    private static final LongAdder UNSUPPORTED = new LongAdder();
    private static final LongAdder CACHE_HITS = new LongAdder();
    private static final LongAdder CACHE_MISSES = new LongAdder();
    private static final LongAdder CACHE_LOOKUP_SKIPPED = new LongAdder();

    private TreeRuntimeTelemetry() {
    }

    public static void record(ProceduralTreeIntegration.Outcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("tree outcome is required");
        }
        ATTEMPTS.increment();
        switch (outcome.result()) {
            case PLACED -> PLACED.increment();
            case COLLISION -> COLLISIONS.increment();
            case QUOTA_REJECTED -> QUOTA_REJECTED.increment();
            case DISABLED -> DISABLED.increment();
            case UNSUPPORTED -> UNSUPPORTED.increment();
        }

        if (!outcome.cacheLookupPerformed()) {
            CACHE_LOOKUP_SKIPPED.increment();
        } else if (outcome.cacheHit()) {
            CACHE_HITS.increment();
        } else {
            CACHE_MISSES.increment();
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(
            ATTEMPTS.sum(),
            PLACED.sum(),
            COLLISIONS.sum(),
            QUOTA_REJECTED.sum(),
            DISABLED.sum(),
            UNSUPPORTED.sum(),
            CACHE_HITS.sum(),
            CACHE_MISSES.sum(),
            CACHE_LOOKUP_SKIPPED.sum()
        );
    }

    public static Snapshot snapshotAndReset() {
        return new Snapshot(
            ATTEMPTS.sumThenReset(),
            PLACED.sumThenReset(),
            COLLISIONS.sumThenReset(),
            QUOTA_REJECTED.sumThenReset(),
            DISABLED.sumThenReset(),
            UNSUPPORTED.sumThenReset(),
            CACHE_HITS.sumThenReset(),
            CACHE_MISSES.sumThenReset(),
            CACHE_LOOKUP_SKIPPED.sumThenReset()
        );
    }

    public static void reset() {
        snapshotAndReset();
    }

    public record Snapshot(
        long attempts,
        long placed,
        long collisions,
        long quotaRejected,
        long disabled,
        long unsupported,
        long cacheHits,
        long cacheMisses,
        long cacheLookupSkipped
    ) {
        public Snapshot {
            if (attempts < 0L || placed < 0L || collisions < 0L
                || quotaRejected < 0L || disabled < 0L || unsupported < 0L
                || cacheHits < 0L || cacheMisses < 0L
                || cacheLookupSkipped < 0L) {
                throw new IllegalArgumentException("negative tree telemetry");
            }
            long outcomes = placed + collisions + quotaRejected
                + disabled + unsupported;
            if (outcomes != attempts) {
                throw new IllegalArgumentException(
                    "tree telemetry attempts/outcomes mismatch"
                );
            }
            long cacheClassified = cacheHits + cacheMisses
                + cacheLookupSkipped;
            if (cacheClassified != attempts) {
                throw new IllegalArgumentException(
                    "tree telemetry cache classification mismatch"
                );
            }
        }

        public long cacheLookups() {
            return cacheHits + cacheMisses;
        }

        public double cacheHitRate() {
            long lookups = cacheLookups();
            return lookups == 0L ? 0.0 : cacheHits / (double)lookups;
        }

        public String asProperties() {
            return "vegetation.tree_v2.attempts=" + attempts + '\n'
                + "vegetation.tree_v2.placed=" + placed + '\n'
                + "vegetation.tree_v2.collisions=" + collisions + '\n'
                + "vegetation.tree_v2.quota_rejected=" + quotaRejected + '\n'
                + "vegetation.tree_v2.disabled=" + disabled + '\n'
                + "vegetation.tree_v2.unsupported=" + unsupported + '\n'
                + "vegetation.tree_v2.cache_hits=" + cacheHits + '\n'
                + "vegetation.tree_v2.cache_misses=" + cacheMisses + '\n'
                + "vegetation.tree_v2.cache_lookup_skipped="
                + cacheLookupSkipped + '\n'
                + "vegetation.tree_v2.cache_hit_rate=" + cacheHitRate() + '\n';
        }
    }
}
