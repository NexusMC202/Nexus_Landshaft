package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.concurrent.atomic.LongAdder;

/** Aggregates the amount of world-state probing performed before tree placement. */
public final class TreePlacementProbeTelemetry {
    private static final LongAdder ENVELOPE_CHECKS = new LongAdder();
    private static final LongAdder ENVELOPE_PROBES = new LongAdder();
    private static final LongAdder FINAL_CHECKS = new LongAdder();
    private static final LongAdder FINAL_PROBES = new LongAdder();

    private TreePlacementProbeTelemetry() {
    }

    public static void recordEnvelope(int probes) {
        requireProbeCount(probes);
        ENVELOPE_CHECKS.increment();
        ENVELOPE_PROBES.add(probes);
    }

    public static void recordFinal(int probes) {
        requireProbeCount(probes);
        FINAL_CHECKS.increment();
        FINAL_PROBES.add(probes);
    }

    public static Snapshot snapshot() {
        return new Snapshot(
            ENVELOPE_CHECKS.sum(),
            ENVELOPE_PROBES.sum(),
            FINAL_CHECKS.sum(),
            FINAL_PROBES.sum()
        );
    }

    public static Snapshot snapshotAndReset() {
        return new Snapshot(
            ENVELOPE_CHECKS.sumThenReset(),
            ENVELOPE_PROBES.sumThenReset(),
            FINAL_CHECKS.sumThenReset(),
            FINAL_PROBES.sumThenReset()
        );
    }

    public static void reset() {
        snapshotAndReset();
    }

    private static void requireProbeCount(int probes) {
        if (probes < 0) {
            throw new IllegalArgumentException("tree probe count cannot be negative");
        }
    }

    public record Snapshot(
        long envelopeChecks,
        long envelopeProbes,
        long finalChecks,
        long finalProbes
    ) {
        public Snapshot {
            if (envelopeChecks < 0L || envelopeProbes < 0L
                || finalChecks < 0L || finalProbes < 0L) {
                throw new IllegalArgumentException("negative tree probe telemetry");
            }
            if (envelopeChecks == 0L && envelopeProbes != 0L) {
                throw new IllegalArgumentException("envelope probes require envelope checks");
            }
            if (finalChecks == 0L && finalProbes != 0L) {
                throw new IllegalArgumentException("final probes require final checks");
            }
        }

        public long totalChecks() {
            return envelopeChecks + finalChecks;
        }

        public long totalProbes() {
            return envelopeProbes + finalProbes;
        }

        public double averageEnvelopeProbes() {
            return envelopeChecks == 0L ? 0.0 : envelopeProbes / (double)envelopeChecks;
        }

        public double averageFinalProbes() {
            return finalChecks == 0L ? 0.0 : finalProbes / (double)finalChecks;
        }
    }
}
