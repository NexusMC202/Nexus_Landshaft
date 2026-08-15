package dev.nexusmc.landscape.worldgen.v2.tree;

/** Verifies lock-free tree runtime counters and cache classification. */
public final class TreeRuntimeTelemetrySelfTest {
    private TreeRuntimeTelemetrySelfTest() {
    }

    public static void main(String[] args) {
        TreeRuntimeTelemetry.reset();

        TreeRuntimeTelemetry.record(new ProceduralTreeIntegration.Outcome(
            ProceduralTreeIntegration.Result.PLACED,
            TreeQualityTier.MID,
            11L,
            true,
            false
        ));
        TreeRuntimeTelemetry.record(new ProceduralTreeIntegration.Outcome(
            ProceduralTreeIntegration.Result.COLLISION,
            TreeQualityTier.MID,
            11L,
            true,
            true
        ));
        TreeRuntimeTelemetry.record(
            ProceduralTreeIntegration.Outcome.withoutLookup(
                ProceduralTreeIntegration.Result.QUOTA_REJECTED,
                TreeQualityTier.HERO,
                22L
            )
        );
        TreeRuntimeTelemetry.record(
            ProceduralTreeIntegration.Outcome.withoutLookup(
                ProceduralTreeIntegration.Result.DISABLED,
                null,
                0L
            )
        );
        TreeRuntimeTelemetry.record(
            ProceduralTreeIntegration.Outcome.withoutLookup(
                ProceduralTreeIntegration.Result.UNSUPPORTED,
                null,
                0L
            )
        );

        TreeRuntimeTelemetry.Snapshot snapshot = TreeRuntimeTelemetry.snapshot();
        require(snapshot.attempts() == 5L, "attempt count mismatch");
        require(snapshot.placed() == 1L, "placed count mismatch");
        require(snapshot.collisions() == 1L, "collision count mismatch");
        require(snapshot.quotaRejected() == 1L, "quota count mismatch");
        require(snapshot.disabled() == 1L, "disabled count mismatch");
        require(snapshot.unsupported() == 1L, "unsupported count mismatch");
        require(snapshot.cacheHits() == 1L, "cache hit mismatch");
        require(snapshot.cacheMisses() == 1L, "cache miss mismatch");
        require(snapshot.cacheLookupSkipped() == 3L,
            "cache skipped mismatch");
        require(snapshot.cacheLookups() == 2L, "cache lookup mismatch");
        require(Math.abs(snapshot.cacheHitRate() - 0.5) < 0.000001,
            "cache hit rate mismatch");
        require(snapshot.asProperties().contains(
            "vegetation.tree_v2.quota_rejected=1"
        ), "telemetry properties missing quota field");

        TreeRuntimeTelemetry.Snapshot reset =
            TreeRuntimeTelemetry.snapshotAndReset();
        require(reset.equals(snapshot), "snapshotAndReset changed values");
        require(TreeRuntimeTelemetry.snapshot().attempts() == 0L,
            "telemetry reset failed");

        System.out.println("TreeRuntimeTelemetrySelfTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
