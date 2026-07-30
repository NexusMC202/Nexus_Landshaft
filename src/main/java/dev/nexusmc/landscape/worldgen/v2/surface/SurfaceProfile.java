package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.List;
import java.util.Set;

/**
 * Immutable, data-only description of one biome surface identity.
 *
 * <p>The profile deliberately stores registry IDs rather than BlockState
 * instances. This keeps profile selection deterministic and testable without
 * retaining a level, chunk, registry access or mutable random source.</p>
 */
public record SurfaceProfile(
    String biomeId,
    String profileId,
    ClimateFamily climate,
    TerrainFamily terrain,
    ElevationBand elevation,
    SlopeBand preferredSlope,
    Layers layers,
    int soilDepth,
    Set<String> visualTraits
) {
    public SurfaceProfile {
        requireIdentifier(biomeId, "biomeId");
        requireIdentifier(profileId, "profileId");
        if (climate == null || terrain == null || elevation == null
            || preferredSlope == null || layers == null) {
            throw new IllegalArgumentException("surface profile fields cannot be null");
        }
        if (soilDepth < 1 || soilDepth > 8) {
            throw new IllegalArgumentException(
                "soilDepth outside [1, 8] for " + biomeId + ": " + soilDepth
            );
        }
        visualTraits = Set.copyOf(visualTraits);
        if (visualTraits.size() < 3) {
            throw new IllegalArgumentException(
                "surface profile needs three visual traits: " + biomeId
            );
        }
    }

    private static void requireIdentifier(String value, String description) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(
                "invalid " + description + ": " + value
            );
        }
    }

    public enum ClimateFamily {
        TEMPERATE,
        BOREAL,
        ALPINE,
        ARID,
        SEASONAL_WARM,
        TROPICAL_HUMID,
        WETLAND,
        MARINE,
        POLAR,
        MYCELIAL,
        UNDERGROUND
    }

    public enum TerrainFamily {
        LOWLAND,
        OLD_HIGHLAND,
        YOUNG_MOUNTAIN,
        DRY_PLATEAU,
        KARST,
        VOLCANIC,
        COAST,
        OCEAN_SHELF,
        OCEAN_DEEP,
        RIVER_CORRIDOR,
        GLACIAL,
        CAVE
    }

    public enum ElevationBand {
        SEA_FLOOR,
        LOW,
        MID,
        HIGH,
        SUMMIT,
        SUBTERRANEAN
    }

    public enum SlopeBand {
        FLAT,
        GENTLE,
        ROLLING,
        STEEP,
        CLIFF,
        SUBMERGED,
        INTERNAL
    }

    public record Layers(
        List<String> top,
        List<String> soil,
        List<String> transition,
        List<String> exposedRock,
        List<String> wet,
        List<String> sediment,
        List<String> coast,
        List<String> alpine
    ) {
        public Layers {
            top = checked(top, "top");
            soil = checked(soil, "soil");
            transition = checked(transition, "transition");
            exposedRock = checked(exposedRock, "exposedRock");
            wet = checked(wet, "wet");
            sediment = checked(sediment, "sediment");
            coast = checked(coast, "coast");
            alpine = checked(alpine, "alpine");
        }

        private static List<String> checked(
            List<String> values,
            String description
        ) {
            List<String> result = List.copyOf(values);
            if (result.isEmpty()) {
                throw new IllegalArgumentException(
                    description + " palette cannot be empty"
                );
            }
            for (String value : result) {
                requireIdentifier(value, description + " block");
            }
            return result;
        }
    }
}
