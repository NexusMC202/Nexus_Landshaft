package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.Objects;

/**
 * Immutable Stage 6 contract describing the physical surface identity of a
 * biome without tying profile selection to chunk generation order.
 *
 * <p>The profile intentionally stores semantic families instead of block
 * instances. Runtime material resolution belongs to a later, registry-aware
 * layer and may provide optional-mod fallbacks.</p>
 */
public record SurfaceProfile(
    String id,
    SoilFamily soil,
    StoneFamily stone,
    ErosionFamily erosion,
    CoastFamily coast,
    RiverFamily river,
    double minimumElevation,
    double maximumElevation,
    double maximumVegetatedSlope,
    double exposedRockSlope,
    double riverInfluenceRadius,
    double coastInfluenceRadius
) {
    public SurfaceProfile {
        id = requireId(id);
        soil = Objects.requireNonNull(soil, "soil");
        stone = Objects.requireNonNull(stone, "stone");
        erosion = Objects.requireNonNull(erosion, "erosion");
        coast = Objects.requireNonNull(coast, "coast");
        river = Objects.requireNonNull(river, "river");

        requireFinite(minimumElevation, "minimumElevation");
        requireFinite(maximumElevation, "maximumElevation");
        requireFinite(maximumVegetatedSlope, "maximumVegetatedSlope");
        requireFinite(exposedRockSlope, "exposedRockSlope");
        requireFinite(riverInfluenceRadius, "riverInfluenceRadius");
        requireFinite(coastInfluenceRadius, "coastInfluenceRadius");

        if (minimumElevation > maximumElevation) {
            throw new IllegalArgumentException(
                "minimumElevation exceeds maximumElevation for " + id
            );
        }
        requireUnit(maximumVegetatedSlope, "maximumVegetatedSlope");
        requireUnit(exposedRockSlope, "exposedRockSlope");
        if (maximumVegetatedSlope > exposedRockSlope) {
            throw new IllegalArgumentException(
                "vegetated slope must not exceed exposed-rock slope for " + id
            );
        }
        requireNonNegative(riverInfluenceRadius, "riverInfluenceRadius");
        requireNonNegative(coastInfluenceRadius, "coastInfluenceRadius");
    }

    public boolean supportsElevation(double elevation) {
        return elevation >= minimumElevation && elevation <= maximumElevation;
    }

    public SurfaceBand bandFor(double slope, double riverWeight, double coastWeight) {
        requireFinite(slope, "slope");
        requireFinite(riverWeight, "riverWeight");
        requireFinite(coastWeight, "coastWeight");
        requireUnit(slope, "slope");
        requireUnit(riverWeight, "riverWeight");
        requireUnit(coastWeight, "coastWeight");

        if (river != RiverFamily.NONE && riverWeight >= 0.5) {
            return SurfaceBand.RIVER_CORRIDOR;
        }
        if (coast != CoastFamily.NONE && coastWeight >= 0.5) {
            return SurfaceBand.COAST;
        }
        if (slope >= exposedRockSlope) {
            return SurfaceBand.EXPOSED_ROCK;
        }
        if (slope > maximumVegetatedSlope) {
            return SurfaceBand.THIN_SOIL;
        }
        return SurfaceBand.FULL_SOIL;
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("surface profile id is empty");
        }
        if (!normalized.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(
                "invalid surface profile id: " + value
            );
        }
        return normalized;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireUnit(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    public enum SurfaceBand {
        FULL_SOIL,
        THIN_SOIL,
        EXPOSED_ROCK,
        RIVER_CORRIDOR,
        COAST
    }

    public enum SoilFamily {
        FERTILE_LOAM,
        ACIDIC_FOREST,
        PEAT_WETLAND,
        AEOLIAN_SAND,
        FROZEN,
        THIN_ALPINE,
        DRY_SEDIMENTARY,
        MYCELIAL,
        SUBMERGED
    }

    public enum StoneFamily {
        ALLUVIAL,
        OLD_SILICATE,
        ALPINE_CRYSTALLINE,
        CARBONATE_KARST,
        ARID_SEDIMENTARY,
        VOLCANIC,
        MARINE,
        ANCIENT_MYCELIAL
    }

    public enum ErosionFamily {
        ACCUMULATION,
        SOFT,
        FLUVIAL,
        MOUNTAIN,
        ARID_CANYON,
        MARINE_OR_GLACIAL
    }

    public enum CoastFamily {
        NONE,
        SANDY,
        GRAVEL,
        ROCKY_CLIFF,
        ESTUARY,
        MANGROVE_LAGOON,
        FROZEN,
        CORAL,
        MYCELIAL
    }

    public enum RiverFamily {
        NONE,
        LOWLAND_MEANDERING,
        BRAIDED_GRAVEL,
        MOUNTAIN,
        SEASONAL_ARROYO,
        WETLAND_DISTRIBUTARY,
        KARST,
        GLACIAL,
        ESTUARY
    }
}
