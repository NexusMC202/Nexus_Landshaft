package dev.nexusmc.landscape.diagnostics;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Opt-in aggregate profiler for Stage 6 generation phases. It is deliberately
 * inert in normal worlds and never emits per-block or per-column log lines.
 */
public final class Stage6Profiler {
    private static final boolean ENABLED =
        "1".equals(System.getenv("NEXUS_LANDSCAPE_PROFILE"))
            || "1".equals(System.getenv("NEXUS_LANDSCAPE_SURVEY"));
    private static final Measurement[] MEASUREMENTS =
        new Measurement[Phase.values().length];

    static {
        for (int index = 0; index < MEASUREMENTS.length; index++) {
            MEASUREMENTS[index] = new Measurement();
        }
    }

    private Stage6Profiler() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static long start() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void record(Phase phase, long startedNanos) {
        if (ENABLED) {
            MEASUREMENTS[phase.ordinal()].record(
                Math.max(0L, System.nanoTime() - startedNanos)
            );
        }
    }

    public static String snapshotAndReset() {
        return snapshot(true);
    }

    public static String snapshot() {
        return snapshot(false);
    }

    public static void reset() {
        snapshot(true);
    }

    private static String snapshot(boolean reset) {
        StringBuilder output = new StringBuilder();
        for (Phase phase : Phase.values()) {
            Snapshot snapshot = MEASUREMENTS[phase.ordinal()].snapshot(reset);
            String key = phase.name().toLowerCase(Locale.ROOT);
            output.append("profile.").append(key).append(".calls=")
                .append(snapshot.calls()).append('\n');
            output.append("profile.").append(key).append(".total_ms=")
                .append(millis(snapshot.totalNanos())).append('\n');
            output.append("profile.").append(key).append(".average_ms=")
                .append(millis(snapshot.averageNanos())).append('\n');
            output.append("profile.").append(key).append(".p50_ms=")
                .append(millis(snapshot.p50Nanos())).append('\n');
            output.append("profile.").append(key).append(".p95_ms=")
                .append(millis(snapshot.p95Nanos())).append('\n');
            output.append("profile.").append(key).append(".maximum_ms=")
                .append(millis(snapshot.maximumNanos())).append('\n');
        }
        return output.toString();
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.4f", nanos / 1_000_000.0);
    }

    public enum Phase {
        VANILLA_SURFACE,
        NEXUS_SURFACE,
        RIVER_WATER,
        VANILLA_PLACED_FEATURES,
        NEXUS_VEGETATION,
        SURFACE_NOISE_SAMPLING,
        SURFACE_BIOME_LOOKUP,
        SURFACE_CONTEXT_PREPARATION,
        SURFACE_BLOCK_REPLACEMENT,
        VEGETATION_SAMPLING,
        VEGETATION_BIOME_LOOKUP,
        VEGETATION_CONTEXT_PREPARATION,
        VEGETATION_BLOCK_PLACEMENT,
        CAVE_ACCENTS
    }

    private static final class Measurement {
        private static final int BUCKETS = 64;
        private final LongAdder calls = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maximumNanos = new AtomicLong();
        private final LongAdder[] histogram = new LongAdder[BUCKETS];

        private Measurement() {
            for (int index = 0; index < histogram.length; index++) {
                histogram[index] = new LongAdder();
            }
        }

        private void record(long nanos) {
            calls.increment();
            totalNanos.add(nanos);
            maximumNanos.accumulateAndGet(nanos, Math::max);
            int bucket = nanos == 0L
                ? 0
                : Math.min(BUCKETS - 1, 64 - Long.numberOfLeadingZeros(nanos));
            histogram[bucket].increment();
        }

        private Snapshot snapshot(boolean reset) {
            long count = reset ? calls.sumThenReset() : calls.sum();
            long total = reset ? totalNanos.sumThenReset() : totalNanos.sum();
            long maximum = reset ? maximumNanos.getAndSet(0L) : maximumNanos.get();
            long[] buckets = new long[BUCKETS];
            for (int index = 0; index < buckets.length; index++) {
                buckets[index] = reset
                    ? histogram[index].sumThenReset()
                    : histogram[index].sum();
            }
            return new Snapshot(
                count,
                total,
                count == 0L ? 0L : total / count,
                Math.min(maximum, percentile(buckets, count, 0.50)),
                Math.min(maximum, percentile(buckets, count, 0.95)),
                maximum
            );
        }

        private static long percentile(
            long[] buckets,
            long count,
            double percentile
        ) {
            if (count == 0L) {
                return 0L;
            }
            long target = Math.max(1L, (long)Math.ceil(count * percentile));
            long cumulative = 0L;
            for (int index = 0; index < buckets.length; index++) {
                cumulative += buckets[index];
                if (cumulative >= target) {
                    return index == 0
                        ? 0L
                        : 1L << Math.min(62, index - 1);
                }
            }
            return 0L;
        }
    }

    private record Snapshot(
        long calls,
        long totalNanos,
        long averageNanos,
        long p50Nanos,
        long p95Nanos,
        long maximumNanos
    ) {
    }
}
