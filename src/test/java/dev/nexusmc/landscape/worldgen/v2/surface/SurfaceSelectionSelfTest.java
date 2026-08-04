package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.List;

public final class SurfaceSelectionSelfTest {
    private SurfaceSelectionSelfTest() {
    }

    public static void main(String[] args) {
        acceptsValidSelection();
        rejectsBlankProfileId();
        rejectsNullZone();
        rejectsInvalidWeight();
        rejectsInvalidDepth();
        rejectsEmptyPalettes();
    }

    private static void acceptsValidSelection() {
        SurfaceSelection selection = new SurfaceSelection(
            "nexus_landscape:test",
            SurfaceSelection.Zone.BASE,
            1.0,
            List.of("minecraft:grass_block"),
            List.of("minecraft:dirt"),
            3
        );
        require(selection.depth() == 3, "valid selection depth changed");
    }

    private static void rejectsBlankProfileId() {
        expectIllegalArgument(() -> selection(" ", SurfaceSelection.Zone.BASE, 1.0, 3));
    }

    private static void rejectsNullZone() {
        expectIllegalArgument(() -> selection("test", null, 1.0, 3));
    }

    private static void rejectsInvalidWeight() {
        expectIllegalArgument(() -> selection("test", SurfaceSelection.Zone.BASE, -0.01, 3));
        expectIllegalArgument(() -> selection("test", SurfaceSelection.Zone.BASE, 1.01, 3));
        expectIllegalArgument(() -> selection("test", SurfaceSelection.Zone.BASE, Double.NaN, 3));
    }

    private static void rejectsInvalidDepth() {
        expectIllegalArgument(() -> selection("test", SurfaceSelection.Zone.BASE, 1.0, 0));
        expectIllegalArgument(() -> selection("test", SurfaceSelection.Zone.BASE, 1.0, 9));
    }

    private static void rejectsEmptyPalettes() {
        expectIllegalArgument(() -> new SurfaceSelection(
            "test",
            SurfaceSelection.Zone.BASE,
            1.0,
            List.of(),
            List.of("minecraft:dirt"),
            3
        ));
        expectIllegalArgument(() -> new SurfaceSelection(
            "test",
            SurfaceSelection.Zone.BASE,
            1.0,
            List.of("minecraft:grass_block"),
            List.of(),
            3
        ));
    }

    private static SurfaceSelection selection(
        String profileId,
        SurfaceSelection.Zone zone,
        double weight,
        int depth
    ) {
        return new SurfaceSelection(
            profileId,
            zone,
            weight,
            List.of("minecraft:grass_block"),
            List.of("minecraft:dirt"),
            depth
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
