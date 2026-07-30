package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.Objects;

/**
 * Immutable vegetation grammar used by Stage 6 profile selection.
 *
 * <p>Values are normalized target weights. Actual placed-feature frequency is
 * resolved later against biome, slope, elevation, hydrology and available
 * registries. The record contains no mutable random state and is safe to share
 * between world-generation threads.</p>
 */
public record VegetationProfile(
    String id,
    CanopyFamily canopy,
    GroundFamily ground,
    double canopyDensity,
    double shrubDensity,
    double groundCoverDensity,
    double clearingShare,
    double riverAffinity,
    double coastAffinity,
    double alpineFadeStart,
    double alpineFadeEnd,
    double maximumTreeSlope,
    boolean supportsOldGrowthClusters
) {
    public VegetationProfile {
        id = requireId(id);
        canopy = Objects.requireNonNull(canopy, "canopy");
        ground = Objects.requireNonNull(ground, "ground");

        requireUnit(canopyDensity, "canopyDensity");
        requireUnit(shrubDensity, "shrubDensity");
        requireUnit(groundCoverDensity, "groundCoverDensity");
        requireUnit(clearingShare, "clearingShare");
        requireSignedUnit(riverAffinity, "riverAffinity");
        requireSignedUnit(coastAffinity, "coastAffinity");
        requireFinite(alpineFadeStart, "alpineFadeStart");
        requireFinite(alpineFadeEnd, "alpineFadeEnd");
        requireUnit(maximumTreeSlope, "maximumTreeSlope");

        if (alpineFadeStart > alpineFadeEnd) {
            throw new IllegalArgumentException(
                "alpineFadeStart exceeds alpineFadeEnd for " + id
            );
        }
        if (canopy == CanopyFamily.NONE && canopyDensity > 0.0) {
            throw new IllegalArgumentException(
                "canopy density must be zero for canopy-free profile " + id
            );
        }
    }

    public double effectiveCanopyDensity(
        double elevation,
        double slope,
        double riverWeight,
        double coastWeight
    ) {
        requireFinite(elevation, "elevation");
        requireUnit(slope, "slope");
        requireUnit(riverWeight, "riverWeight");
        requireUnit(coastWeight, "coastWeight");

        if (canopy == CanopyFamily.NONE || slope > maximumTreeSlope) {
            return 0.0;
        }

        double alpineFactor = alpineFactor(elevation);
        double hydrologyFactor = 1.0
            + riverAffinity * riverWeight
            + coastAffinity * coastWeight;
        hydrologyFactor = clamp(hydrologyFactor, 0.0, 2.0);

        return clamp(canopyDensity * alpineFactor * hydrologyFactor, 0.0, 1.0);
    }

    public double alpineFactor(double elevation) {
        requireFinite(elevation, "elevation");
        if (elevation <= alpineFadeStart) {
            return 1.0;
        }
        if (elevation >= alpineFadeEnd) {
            return 0.0;
        }
        double span = alpineFadeEnd - alpineFadeStart;
        if (span <= 0.0) {
            return 0.0;
        }
        return 1.0 - (elevation - alpineFadeStart) / span;
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("vegetation profile id is empty");
        }
        if (!normalized.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(
                "invalid vegetation profile id: " + value
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
        requireFinite(value, name);
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    private static void requireSignedUnit(double value, String name) {
        requireFinite(value, name);
        if (value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [-1, 1]");
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum CanopyFamily {
        NONE,
        TEMPERATE_MIXED,
        OLD_GROWTH_TEMPERATE,
        BIRCH_WOODLAND,
        DARK_FOREST,
        COLD_CONIFER,
        ALPINE_CONIFER,
        WARM_DRY_WOODLAND,
        SAVANNA_OPEN,
        TROPICAL_HUMID,
        SWAMP,
        MANGROVE,
        CHERRY_HIGHLAND,
        MYCELIAL,
        COASTAL_ISLAND
    }

    public enum GroundFamily {
        SPARSE,
        TEMPERATE_GRASS,
        FOREST_FLOOR,
        MOSSY,
        FERN_RICH,
        FLOWER_MEADOW,
        WETLAND,
        DRY_GRASS,
        DESERT_SCRUB,
        ALPINE,
        SNOW_MARGIN,
        VOLCANIC_PIONEER,
        MYCELIAL,
        COASTAL
    }
}
