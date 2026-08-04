package dev.nexusmc.landscape.worldgen.v2.surface;

/**
 * Regression checks for normalized surface context inputs and derived flags.
 */
public final class SurfaceContextSelfTest {
    private SurfaceContextSelfTest() {
    }

    public static void main(String[] args) {
        rejectsInvalidSlope();
        rejectsInvalidUnitFields();
        rejectsInvalidIdentifiers();
        rejectsInvalidDistances();
        checksDerivedFlags();
    }

    private static void rejectsInvalidSlope() {
        expectFailure(() -> context(-0.01, 0.0, 64.0, 0.0, 0.0, 80.0, 0.0));
        expectFailure(() -> context(1.01, 0.0, 64.0, 0.0, 0.0, 80.0, 0.0));
        expectFailure(() -> context(Double.NaN, 0.0, 64.0, 0.0, 0.0, 80.0, 0.0));
        context(0.0, 0.0, 64.0, 0.0, 0.0, 80.0, 0.0);
        context(1.0, 0.0, 64.0, 0.0, 0.0, 80.0, 0.0);
    }

    private static void rejectsInvalidUnitFields() {
        expectFailure(() -> context(0.1, -0.01, 64.0, 0.0, 0.0, 80.0, 0.0));
        expectFailure(() -> context(0.1, 1.01, 64.0, 0.0, 0.0, 80.0, 0.0));
        expectFailure(() -> context(0.1, 0.0, 64.0, 1.01, 0.0, 80.0, 0.0));
        expectFailure(() -> context(0.1, 0.0, 64.0, 0.0, 1.01, 80.0, 0.0));
    }

    private static void rejectsInvalidIdentifiers() {
        expectFailure(() -> createContextWithIdentifiers(null, "plains"));
        expectFailure(() -> createContextWithIdentifiers("", "plains"));
        expectFailure(() -> createContextWithIdentifiers("   ", "plains"));
        expectFailure(() -> createContextWithIdentifiers("minecraft:plains", null));
        expectFailure(() -> createContextWithIdentifiers("minecraft:plains", ""));
        expectFailure(() -> createContextWithIdentifiers("minecraft:plains", "  "));
        createContextWithIdentifiers("minecraft:plains", "plains");
    }

    private static void rejectsInvalidDistances() {
        expectFailure(() -> context(0.1, 0.0, -0.01, 0.0, 0.0, 80.0, 0.0));
        expectFailure(() -> context(0.1, 0.0, Double.NaN, 0.0, 0.0, 80.0, 0.0));
        expectFailure(() -> contextWithOceanDistance(-0.01));
        expectFailure(() -> contextWithOceanDistance(Double.POSITIVE_INFINITY));
        context(0.1, 0.0, 0.0, 0.0, 0.0, 80.0, 0.0);
        contextWithOceanDistance(0.0);
    }

    private static void checksDerivedFlags() {
        SurfaceContext channel = context(0.1, 0.50, 64.0, 0.0, 0.0, 80.0, 0.0);
        require(channel.activeChannel(), "river mask threshold must activate channel");
        require(!channel.wetBank(), "active channel cannot also be a wet bank");

        SurfaceContext bankByDistance = context(0.1, 0.49, 18.0, 0.0, 0.0, 80.0, 0.0);
        require(bankByDistance.wetBank(), "near-channel distance must activate wet bank");

        SurfaceContext bankByGroundwater = context(0.1, 0.0, 64.0, 0.72, 0.0, 80.0, 0.0);
        require(bankByGroundwater.wetBank(), "groundwater threshold must activate wet bank");

        require(context(0.16, 0.0, 64.0, 0.0, 0.0, 80.0, 0.0).exposedSlope(),
            "slope threshold must activate exposed slope");
        require(context(0.1, 0.0, 64.0, 0.0, 0.50, 80.0, 0.0).alpineExposure(),
            "alpine influence threshold must activate alpine exposure");
        require(context(0.1, 0.0, 64.0, 0.0, 0.0, 150.0, 0.0).alpineExposure(),
            "elevation threshold must activate alpine exposure");
        require(context(0.1, 0.0, 64.0, 0.0, 0.0, 80.0, 0.55).alpineExposure(),
            "glacier threshold must activate alpine exposure");
    }

    private static SurfaceContext context(
        double slope,
        double riverMask,
        double riverDistance,
        double groundwater,
        double alpineInfluence,
        double elevation,
        double glacierWeight
    ) {
        return createContext(
            "minecraft:plains",
            "plains",
            slope,
            riverMask,
            riverDistance,
            64.0,
            groundwater,
            alpineInfluence,
            elevation,
            glacierWeight
        );
    }

    private static SurfaceContext contextWithOceanDistance(
        double oceanDistance
    ) {
        return createContext(
            "minecraft:plains",
            "plains",
            0.1,
            0.0,
            64.0,
            oceanDistance,
            0.0,
            0.0,
            80.0,
            0.0
        );
    }

    private static SurfaceContext createContextWithIdentifiers(
        String biomeKey,
        String terrainProvince
    ) {
        return createContext(
            biomeKey,
            terrainProvince,
            0.1,
            0.0,
            64.0,
            64.0,
            0.0,
            0.0,
            80.0,
            0.0
        );
    }

    private static SurfaceContext createContext(
        String biomeKey,
        String terrainProvince,
        double slope,
        double riverMask,
        double riverDistance,
        double oceanDistance,
        double groundwater,
        double alpineInfluence,
        double elevation,
        double glacierWeight
    ) {
        return new SurfaceContext(
            1L,
            10,
            20,
            (int)elevation,
            biomeKey,
            terrainProvince,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            elevation,
            0.5,
            slope,
            riverDistance,
            riverMask,
            riverMask,
            0.0,
            0.0,
            oceanDistance,
            groundwater,
            0.0,
            glacierWeight,
            alpineInfluence,
            0.0,
            0.0,
            0.0,
            0.0,
            0.5,
            0.5
        );
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
