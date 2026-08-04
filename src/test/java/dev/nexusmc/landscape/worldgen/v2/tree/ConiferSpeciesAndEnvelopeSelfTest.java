package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;

/** Verifies species separation and cheap pre-voxel placement estimates. */
public final class ConiferSpeciesAndEnvelopeSelfTest {
    private ConiferSpeciesAndEnvelopeSelfTest() {
    }

    public static void main(String[] args) {
        verifySpeciesMapping();
        verifySpeciesSilhouetteContract();
        verifyEnvelopeContract();
        System.out.println("ConiferSpeciesAndEnvelopeSelfTest: PASS");
    }

    private static void verifySpeciesMapping() {
        require(
            ConiferSpeciesProfile.fromShape(
                VegetationProfile.TreeShape.SPRUCE_CONICAL
            ) == ConiferSpeciesProfile.SPRUCE,
            "spruce mapping mismatch"
        );
        require(
            ConiferSpeciesProfile.fromShape(
                VegetationProfile.TreeShape.PINE_TALL
            ) == ConiferSpeciesProfile.PINE,
            "pine mapping mismatch"
        );
        expectFailure(() -> ConiferSpeciesProfile.fromShape(
            VegetationProfile.TreeShape.OAK_ROUNDED
        ));
    }

    private static void verifySpeciesSilhouetteContract() {
        ConiferSpeciesProfile spruce = ConiferSpeciesProfile.SPRUCE;
        ConiferSpeciesProfile pine = ConiferSpeciesProfile.PINE;
        require(pine.heightMultiplier() > spruce.heightMultiplier(),
            "pine must be taller than spruce");
        require(pine.crownStartOffset() > spruce.crownStartOffset(),
            "pine crown must begin higher");
        require(pine.upperCrownMultiplier() > spruce.upperCrownMultiplier(),
            "pine must emphasize the upper crown");
        require(spruce.foliageDensityMultiplier() > pine.foliageDensityMultiplier(),
            "spruce foliage must be denser");
        require(spruce.lowerBranchMultiplier() > pine.lowerBranchMultiplier(),
            "spruce must retain more lower branches");
    }

    private static void verifyEnvelopeContract() {
        TreeEnvironment environment = new TreeEnvironment(
            0.18, 0.68, 0.42, 0.08,
            -0.32, 0.14, 0.64, -0.10, 96
        );
        TreePlacementEnvelope spruce = TreePlacementEnvelope.estimate(
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.MID,
            environment,
            false
        );
        TreePlacementEnvelope identical = TreePlacementEnvelope.estimate(
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.MID,
            environment,
            false
        );
        TreePlacementEnvelope pine = TreePlacementEnvelope.estimate(
            ConiferSpeciesProfile.PINE,
            TreeQualityTier.MID,
            environment,
            false
        );
        TreePlacementEnvelope hero = TreePlacementEnvelope.estimate(
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.HERO,
            environment,
            true
        );

        require(spruce.equals(identical), "envelope planning is not deterministic");
        require(pine.height() > spruce.height(), "pine envelope must be taller");
        require(hero.horizontalRadius() >= spruce.horizontalRadius(),
            "HERO envelope cannot be narrower than MID");
        require(hero.probeCount() > spruce.probeCount(),
            "HERO must use more conservative probes");
        require(spruce.probeCount() < spruce.estimatedVolume(),
            "cheap envelope probes must be below full volume");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected validation failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
