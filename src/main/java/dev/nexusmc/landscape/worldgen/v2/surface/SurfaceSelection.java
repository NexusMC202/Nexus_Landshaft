package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.List;

/**
 * Pure result of resolving a profile against local surface fields.
 */
public record SurfaceSelection(
    String profileId,
    Zone zone,
    List<String> topPalette,
    List<String> substratePalette,
    int depth
) {
    public SurfaceSelection {
        topPalette = List.copyOf(topPalette);
        substratePalette = List.copyOf(substratePalette);
        if (topPalette.isEmpty() || substratePalette.isEmpty()) {
            throw new IllegalArgumentException("selected palettes cannot be empty");
        }
        if (depth < 1 || depth > 8) {
            throw new IllegalArgumentException("invalid selected depth: " + depth);
        }
    }

    public enum Zone {
        CHANNEL,
        LAKE_SHORE,
        WET_BANK,
        COAST,
        VOLCANIC,
        ALPINE,
        EXPOSED_SLOPE,
        BASE
    }
}
