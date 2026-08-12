package dev.nexusmc.landscape.worldgen.v2.tree;

/** Verifies cache lookup and fallback semantics at the tree runtime boundary. */
public final class TreeRuntimeOutcomeSelfTest {
    private TreeRuntimeOutcomeSelfTest() {
    }

    public static void main(String[] args) {
        verifyPlacementLookupSemantics();
        verifyOutcomeFallbackSemantics();
        verifyTelemetryClassifiesLookupStates();
        System.out.println("TreeRuntimeOutcomeSelfTest: PASS");
    }

    private static void verifyPlacementLookupSemantics() {
        ProceduralTreeRuntime.Placement skipped =
            ProceduralTreeRuntime.Placement.withoutLookup(false);
        require(!skipped.placed(), "early reject must not be placed");
        require(!skipped.cacheLookupPerformed(),
            "early reject must not report a cache lookup");
        require(!skipped.cacheHit(), "early reject cannot be a cache hit");

        ProceduralTreeRuntime.Placement miss =
            ProceduralTreeRuntime.Placement.afterLookup(false, false);
        require(miss.cacheLookupPerformed(), "cache miss must report lookup");
        require(!miss.cacheHit(), "cache miss reported as hit");

        ProceduralTreeRuntime.Placement hit =
            ProceduralTreeRuntime.Placement.afterLookup(true, true);
        require(hit.cacheLookupPerformed(), "cache hit must report lookup");
        require(hit.cacheHit(), "cache hit flag lost");

        expectFailure(
            () -> new ProceduralTreeRuntime.Placement(false, false, true),
            "cache hit without lookup must be rejected"
        );
    }

    private static void verifyOutcomeFallbackSemantics() {
        ProceduralTreeIntegration.Outcome quota =
            ProceduralTreeIntegration.Outcome.withoutLookup(
                ProceduralTreeIntegration.Result.QUOTA_REJECTED,
                TreeQualityTier.HERO,
                7L
            );
        require(!quota.cacheLookupPerformed(),
            "quota rejection must happen before model cache lookup");
        require(!quota.shouldFallback(),
            "quota rejection must not invoke legacy tree fallback");

        ProceduralTreeIntegration.Outcome collision =
            new ProceduralTreeIntegration.Outcome(
                ProceduralTreeIntegration.Result.COLLISION,
                TreeQualityTier.MID,
                11L,
                true,
                false
            );
        require(collision.cacheMiss(), "collision lookup miss not recognized");
        require(collision.shouldFallback(),
            "collision must retain legacy fallback behavior");

        ProceduralTreeIntegration.Outcome disabled =
            ProceduralTreeIntegration.Outcome.withoutLookup(
                ProceduralTreeIntegration.Result.DISABLED,
                null,
                0L
            );
        require(disabled.shouldFallback(),
            "disabled V2 path must retain legacy fallback");
    }

    private static void verifyTelemetryClassifiesLookupStates() {
        TreeRuntimeTelemetry.reset();
        TreeRuntimeTelemetry.record(
            ProceduralTreeIntegration.Outcome.withoutLookup(
                ProceduralTreeIntegration.Result.QUOTA_REJECTED,
                TreeQualityTier.HERO,
                1L
            )
        );
        TreeRuntimeTelemetry.record(new ProceduralTreeIntegration.Outcome(
            ProceduralTreeIntegration.Result.COLLISION,
            TreeQualityTier.MID,
            2L,
            true,
            false
        ));
        TreeRuntimeTelemetry.record(new ProceduralTreeIntegration.Outcome(
            ProceduralTreeIntegration.Result.PLACED,
            TreeQualityTier.BASIC,
            3L,
            true,
            true
        ));

        TreeRuntimeTelemetry.Snapshot snapshot = TreeRuntimeTelemetry.snapshot();
        require(snapshot.cacheLookupSkipped() == 1L,
            "pre-cache quota reject must count as lookup skipped");
        require(snapshot.cacheMisses() == 1L,
            "real cache miss count mismatch");
        require(snapshot.cacheHits() == 1L,
            "real cache hit count mismatch");
        require(snapshot.quotaRejected() == 1L,
            "quota rejection telemetry mismatch");
        TreeRuntimeTelemetry.reset();
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
