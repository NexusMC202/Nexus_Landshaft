package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.List;
import java.util.Set;

/**
 * Regression checks for immutable surface profile invariants.
 */
public final class SurfaceProfileInvariantSelfTest {
    private SurfaceProfileInvariantSelfTest() {
    }

    public static void main(String[] args) {
        rejectsInvalidTraits();
        rejectsInvalidPalettes();
        acceptsValidProfile();
    }

    private static void rejectsInvalidTraits() {
        expectFailure(() -> profile(null));
        expectFailure(() -> profile(Set.of("one", "two")));
        expectFailure(() -> profile(Set.of("one", "two", "")));
        expectFailure(() -> profile(Set.of("one", "two", "   ")));
    }

    private static void rejectsInvalidPalettes() {
        expectFailure(() -> layers(null));
        expectFailure(() -> layers(List.of()));
        expectFailure(() -> layers(List.of("not-an-id")));
    }

    private static void acceptsValidProfile() {
        SurfaceProfile profile = profile(Set.of("rocky", "windy", "dry"));
        require(profile.visualTraits().size() == 3,
            "valid traits must be retained");
        require(profile.layers().top().equals(List.of("minecraft:stone")),
            "valid palette must be retained");
    }

    private static SurfaceProfile profile(Set<String> traits) {
        return new SurfaceProfile(
            "minecraft:plains",
            "nexus_landscape:test",
            SurfaceProfile.ClimateFamily.TEMPERATE,
            SurfaceProfile.TerrainFamily.LOWLAND,
            SurfaceProfile.ElevationBand.LOW,
            SurfaceProfile.SlopeBand.GENTLE,
            layers(List.of("minecraft:stone")),
            3,
            traits
        );
    }

    private static SurfaceProfile.Layers layers(List<String> top) {
        List<String> stone = List.of("minecraft:stone");
        return new SurfaceProfile.Layers(
            top,
            stone,
            stone,
            stone,
            stone,
            stone,
            stone,
            stone
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
