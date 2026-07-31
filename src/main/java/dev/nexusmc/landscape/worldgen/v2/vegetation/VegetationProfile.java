package dev.nexusmc.landscape.worldgen.v2.vegetation;

import java.util.List;
import java.util.Set;

/**
 * Immutable vegetation grammar parameters. Densities are probabilities in
 * [0,1], interpreted by the coordinate-driven runtime pass.
 */
public record VegetationProfile(
    String biomeId,
    String profileId,
    Family family,
    double treeDensity,
    double shrubDensity,
    double groundDensity,
    double flowerDensity,
    double deadwoodDensity,
    double rockDensity,
    double oldGrowthDensity,
    double clearingShare,
    double maxTreeSlope,
    int treeLineY,
    List<TreeShape> treeShapes,
    Set<String> groundPalette,
    boolean terrestrial,
    String intentionalAbsence
) {
    public VegetationProfile {
        if (biomeId == null || profileId == null || family == null) {
            throw new IllegalArgumentException("vegetation profile identity is null");
        }
        check(treeDensity, "treeDensity");
        check(shrubDensity, "shrubDensity");
        check(groundDensity, "groundDensity");
        check(flowerDensity, "flowerDensity");
        check(deadwoodDensity, "deadwoodDensity");
        check(rockDensity, "rockDensity");
        check(oldGrowthDensity, "oldGrowthDensity");
        check(clearingShare, "clearingShare");
        check(maxTreeSlope, "maxTreeSlope");
        treeShapes = List.copyOf(treeShapes);
        groundPalette = Set.copyOf(groundPalette);
        intentionalAbsence = intentionalAbsence == null ? "" : intentionalAbsence;
        if (treeShapes.isEmpty() && treeDensity > 0.0) {
            throw new IllegalArgumentException("tree density without shapes: " + biomeId);
        }
        if (!terrestrial && intentionalAbsence.isBlank()) {
            throw new IllegalArgumentException(
                "non-terrestrial profile needs an explicit reason: " + biomeId
            );
        }
    }

    private static void check(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " outside [0,1]: " + value);
        }
    }

    public enum Family {
        TEMPERATE_FOREST,
        BOREAL_FOREST,
        WARM_DRY_WOODLAND,
        TROPICAL_HUMID,
        WETLAND,
        SAVANNA,
        ARID,
        VOLCANIC_PIONEER,
        MYCELIAL,
        COASTAL,
        MEADOW,
        ALPINE_MARGIN,
        POLAR,
        AQUATIC,
        LUSH_CAVE,
        DRIPSTONE_CAVE,
        DEEP_DARK
    }

    public enum TreeShape {
        OAK_ROUNDED,
        BIRCH_COLUMN,
        DARK_OAK_BROAD,
        SPRUCE_CONICAL,
        PINE_TALL,
        ACACIA_FLAT,
        JUNGLE_EMERGENT,
        CHERRY_TERRACE,
        MANGROVE_ROOTED,
        GIANT_MUSHROOM
    }
}
