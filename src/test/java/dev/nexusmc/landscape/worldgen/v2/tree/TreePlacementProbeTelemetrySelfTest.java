package dev.nexusmc.landscape.worldgen.v2.tree;

/** Verifies tree placement probe aggregation and reset semantics. */
public final class TreePlacementProbeTelemetrySelfTest {
    private TreePlacementProbeTelemetrySelfTest() {
    }

    public static void main(String[] args) {
        TreePlacementProbeTelemetry.reset();
        require(TreePlacementProbeTelemetry.snapshot().totalProbes() == 0L,
            "probe telemetry did not reset to zero");

        TreePlacementProbeTelemetry.recordEnvelope(5);
        TreePlacementProbeTelemetry.recordEnvelope(11);
        TreePlacementProbeTelemetry.recordFinal(37);
        TreePlacementProbeTelemetry.recordFinal(13);

        TreePlacementProbeTelemetry.Snapshot snapshot =
            TreePlacementProbeTelemetry.snapshot();
        require(snapshot.envelopeChecks() == 2L,
            "envelope check count mismatch");
        require(snapshot.envelopeProbes() == 16L,
            "envelope probe count mismatch");
        require(snapshot.finalChecks() == 2L,
            "final check count mismatch");
        require(snapshot.finalProbes() == 50L,
            "final probe count mismatch");
        require(snapshot.totalChecks() == 4L,
            "total check count mismatch");
        require(snapshot.totalProbes() == 66L,
            "total probe count mismatch");
        require(Math.abs(snapshot.averageEnvelopeProbes() - 8.0) < 1.0E-12,
            "average envelope probes mismatch");
        require(Math.abs(snapshot.averageFinalProbes() - 25.0) < 1.0E-12,
            "average final probes mismatch");

        TreePlacementProbeTelemetry.Snapshot reset =
            TreePlacementProbeTelemetry.snapshotAndReset();
        require(reset.equals(snapshot),
            "snapshotAndReset changed probe telemetry values");
        require(TreePlacementProbeTelemetry.snapshot().totalChecks() == 0L,
            "snapshotAndReset did not clear checks");
        require(TreePlacementProbeTelemetry.snapshot().totalProbes() == 0L,
            "snapshotAndReset did not clear probes");

        expectFailure(() -> TreePlacementProbeTelemetry.recordEnvelope(-1));
        expectFailure(() -> TreePlacementProbeTelemetry.recordFinal(-1));

        System.out.println("TreePlacementProbeTelemetrySelfTest: PASS");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected validation failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
