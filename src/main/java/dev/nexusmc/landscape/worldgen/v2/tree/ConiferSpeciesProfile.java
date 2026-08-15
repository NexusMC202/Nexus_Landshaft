package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;

/**
 * Species-level modifiers applied before anatomical planning. The shared tree
 * engine remains identical, while spruce and pine resolve visibly different
 * silhouettes, crown positions, foliage density and explicit materials.
 */
public enum ConiferSpeciesProfile {
    SPRUCE(
        TreeMaterialProfile.SPRUCE,
        1.00,
        -0.06,
        0.92,
        1.00,
        1.18,
        0.96,
        0.86
    ),
    PINE(
        TreeMaterialProfile.SPRUCE,
        1.16,
        0.20,
        1.10,
        0.74,
        0.78,
        1.06,
        1.18
    ),
    OAK(TreeMaterialProfile.OAK, 0.92, -0.18, 1.12, 1.08, 1.08, 1.10, 0.92),
    BIRCH(TreeMaterialProfile.BIRCH, 1.08, 0.12, 0.78, 0.72, 0.86, 0.72, 1.08),
    DARK_OAK(TreeMaterialProfile.DARK_OAK, 0.88, -0.22, 1.28, 1.16, 1.18, 1.28, 0.88),
    JUNGLE(TreeMaterialProfile.JUNGLE, 1.34, 0.28, 1.18, 0.72, 1.12, 1.18, 1.24),
    ACACIA(TreeMaterialProfile.ACACIA, 0.86, 0.18, 1.36, 0.58, 0.72, 0.86, 1.34),
    CHERRY(TreeMaterialProfile.CHERRY, 0.90, -0.04, 1.22, 0.84, 1.20, 0.88, 1.10),
    MANGROVE(TreeMaterialProfile.MANGROVE, 0.94, -0.16, 1.16, 1.12, 1.14, 1.14, 0.96
    );

    private final TreeMaterialProfile materialProfile;
    private final double heightMultiplier;
    private final double crownStartOffset;
    private final double branchLengthMultiplier;
    private final double lowerBranchMultiplier;
    private final double foliageDensityMultiplier;
    private final double trunkRadiusMultiplier;
    private final double upperCrownMultiplier;

    ConiferSpeciesProfile(
        TreeMaterialProfile materialProfile,
        double heightMultiplier,
        double crownStartOffset,
        double branchLengthMultiplier,
        double lowerBranchMultiplier,
        double foliageDensityMultiplier,
        double trunkRadiusMultiplier,
        double upperCrownMultiplier
    ) {
        if (materialProfile == null) {
            throw new IllegalArgumentException("materialProfile is required");
        }
        this.materialProfile = materialProfile;
        this.heightMultiplier = positive(heightMultiplier, "heightMultiplier");
        this.crownStartOffset = finite(crownStartOffset, "crownStartOffset");
        this.branchLengthMultiplier = positive(
            branchLengthMultiplier, "branchLengthMultiplier"
        );
        this.lowerBranchMultiplier = positive(
            lowerBranchMultiplier, "lowerBranchMultiplier"
        );
        this.foliageDensityMultiplier = positive(
            foliageDensityMultiplier, "foliageDensityMultiplier"
        );
        this.trunkRadiusMultiplier = positive(
            trunkRadiusMultiplier, "trunkRadiusMultiplier"
        );
        this.upperCrownMultiplier = positive(
            upperCrownMultiplier, "upperCrownMultiplier"
        );
    }

    public static ConiferSpeciesProfile fromShape(
        VegetationProfile.TreeShape shape
    ) {
        return switch (shape) {
            case SPRUCE_CONICAL -> SPRUCE;
            case PINE_TALL -> PINE;
            case OAK_ROUNDED -> OAK;
            case BIRCH_COLUMN -> BIRCH;
            case DARK_OAK_BROAD -> DARK_OAK;
            case JUNGLE_EMERGENT -> JUNGLE;
            case ACACIA_FLAT -> ACACIA;
            case CHERRY_TERRACE -> CHERRY;
            case MANGROVE_ROOTED -> MANGROVE;
            case GIANT_MUSHROOM -> throw new IllegalArgumentException(
                "unsupported procedural tree shape: " + shape
            );
        };
    }

    public TreeMaterialProfile materialProfile() {
        return materialProfile;
    }

    public double heightMultiplier() {
        return heightMultiplier;
    }

    public double crownStartOffset() {
        return crownStartOffset;
    }

    public double branchLengthMultiplier() {
        return branchLengthMultiplier;
    }

    public double lowerBranchMultiplier() {
        return lowerBranchMultiplier;
    }

    public double foliageDensityMultiplier() {
        return foliageDensityMultiplier;
    }

    public double trunkRadiusMultiplier() {
        return trunkRadiusMultiplier;
    }

    public double upperCrownMultiplier() {
        return upperCrownMultiplier;
    }

    private static double positive(double value, String name) {
        finite(value, name);
        if (value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }
}
