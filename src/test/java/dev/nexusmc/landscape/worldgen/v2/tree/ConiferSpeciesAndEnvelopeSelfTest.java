package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;

/** Verifies species separation and cheap pre-voxel placement estimates. */
public final class ConiferSpeciesAndEnvelopeSelfTest {
    private ConiferSpeciesAndEnvelopeSelfTest() {
    }

    public static void main(String[] args) {
        verifySpeciesMapping();
        verifySpeciesSilhouetteContract();
        verifyGeneratedSkeletonsDiffer();
        verifySpeciesFoliageDiffers();
        verifyEnvelopeContract();
        verifyRegionalQuotas();
        verifyBoundedModelCache();
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

    private static void verifyGeneratedSkeletonsDiffer() {
        SpeciesModels models = speciesModels();
        int trunkSections = models.trunkSections();

        require(models.spruceGraph().fingerprint()
                != models.pineGraph().fingerprint(),
            "spruce and pine graphs must not be identical");
        require(maxY(models.pineGraph()) > maxY(models.spruceGraph()),
            "generated pine must be taller than generated spruce");
        require(firstCrownY(models.pineGraph(), trunkSections)
                > firstCrownY(models.spruceGraph(), trunkSections),
            "generated pine crown must begin higher");
    }

    private static void verifySpeciesFoliageDiffers() {
        SpeciesModels models = speciesModels();
        VoxelTreeModel spruce = TreeVoxelizer.voxelize(
            models.spruceGraph(),
            TreeQualityTier.MID,
            models.seed(),
            ConiferSpeciesProfile.SPRUCE
        );
        VoxelTreeModel pine = TreeVoxelizer.voxelize(
            models.pineGraph(),
            TreeQualityTier.MID,
            models.seed(),
            ConiferSpeciesProfile.PINE
        );

        require(!spruce.leaves().isEmpty(), "spruce foliage is empty");
        require(!pine.leaves().isEmpty(), "pine foliage is empty");
        require(spruce.fingerprint() != pine.fingerprint(),
            "species voxel models must not be identical");

        double spruceShare = lowerFoliageShare(spruce);
        double pineShare = lowerFoliageShare(pine);
        require(spruceShare > pineShare,
            "spruce must retain more lower foliage than pine");
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

    private static void verifyRegionalQuotas() {
        long worldSeed = 0x51554F5441534545L;
        int basic = 0;
        int mid = 0;
        int hero = 0;
        for (int cellZ = 0; cellZ < TreeRegionalQuotaPolicy.REGION_CELLS; cellZ++) {
            for (int cellX = 0; cellX < TreeRegionalQuotaPolicy.REGION_CELLS; cellX++) {
                int x = cellX * TreeRegionalQuotaPolicy.CELL_SIZE + 8;
                int z = cellZ * TreeRegionalQuotaPolicy.CELL_SIZE + 8;
                if (TreeRegionalQuotaPolicy.allows(
                    worldSeed, x, z, TreeQualityTier.BASIC
                )) {
                    basic++;
                }
                if (TreeRegionalQuotaPolicy.allows(
                    worldSeed, x, z, TreeQualityTier.MID
                )) {
                    mid++;
                }
                if (TreeRegionalQuotaPolicy.allows(
                    worldSeed, x, z, TreeQualityTier.HERO
                )) {
                    hero++;
                }
            }
        }
        require(basic == 64, "BASIC quota must admit every candidate cell");
        require(mid == 8, "MID regional quota must admit exactly 8 cells");
        require(hero == 1, "HERO regional quota must admit exactly 1 cell");

        boolean forward = TreeRegionalQuotaPolicy.allows(
            worldSeed, 40, 72, TreeQualityTier.HERO
        );
        boolean repeated = TreeRegionalQuotaPolicy.allows(
            worldSeed, 40, 72, TreeQualityTier.HERO
        );
        require(forward == repeated, "regional quota is not deterministic");
    }

    private static void verifyBoundedModelCache() {
        TreeModelCache.clear();
        ProceduralTreePlan firstPlan = planAt(120, -88);
        VoxelTreeModel first = TreeModelCache.getOrCreate(firstPlan);
        VoxelTreeModel repeated = TreeModelCache.getOrCreate(firstPlan);
        require(first == repeated, "cache must reuse the admitted tree model");
        require(TreeModelCache.size() == 1, "cache must contain one admitted model");
        require(TreeModelCache.capacity() == TreeModelCache.DEFAULT_CAPACITY,
            "tree cache capacity mismatch");

        ProceduralTreePlan secondPlan = planAt(136, -88);
        VoxelTreeModel second = TreeModelCache.getOrCreate(secondPlan);
        require(second.fingerprint() != first.fingerprint(),
            "different tree plans must not alias in cache");
        require(TreeModelCache.size() == 2,
            "second plan must add one cache entry");
        TreeModelCache.clear();
        require(TreeModelCache.size() == 0, "tree cache clear failed");
    }

    private static ProceduralTreePlan planAt(int x, int z) {
        return ProceduralTreePlan.create(
            0x4341434845534545L,
            x,
            z,
            VegetationProfile.TreeShape.SPRUCE_CONICAL,
            false,
            0.16,
            0.14,
            0.66,
            0.44,
            0.08,
            -0.28,
            0.12,
            0.62,
            -0.08,
            96
        );
    }

    private static SpeciesModels speciesModels() {
        long seed = 0x535045434945534CL;
        TreeEnvironment environment = new TreeEnvironment(
            0.14, 0.66, 0.46, 0.07,
            -0.28, 0.12, 0.62, -0.08, 96
        );
        TreeLifeHistory history = TreeLifeHistory.generate(
            seed, TreeQualityTier.MID, environment
        );
        BranchGraph spruce = SpeciesConiferBranchGenerator.generate(
            seed,
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.MID,
            environment,
            history
        );
        BranchGraph pine = SpeciesConiferBranchGenerator.generate(
            seed,
            ConiferSpeciesProfile.PINE,
            TreeQualityTier.MID,
            environment,
            history
        );
        int trunkSections = TrunkPlan.resolve(
            seed, TreeQualityTier.MID, environment, history
        ).sections();
        return new SpeciesModels(seed, spruce, pine, trunkSections);
    }

    private static double lowerFoliageShare(VoxelTreeModel model) {
        int maxY = Integer.MIN_VALUE;
        for (VoxelTreeModel.Voxel leaf : model.leaves()) {
            maxY = Math.max(maxY, leaf.y());
        }
        double cutoff = maxY * 0.56;
        int lower = 0;
        for (VoxelTreeModel.Voxel leaf : model.leaves()) {
            if (leaf.y() < cutoff) {
                lower++;
            }
        }
        return lower / (double)model.leaves().size();
    }

    private static double maxY(BranchGraph graph) {
        double result = Double.NEGATIVE_INFINITY;
        for (BranchGraph.Segment segment : graph.segments()) {
            result = Math.max(result, Math.max(segment.startY(), segment.endY()));
        }
        return result;
    }

    private static double firstCrownY(BranchGraph graph, int trunkSections) {
        double result = Double.POSITIVE_INFINITY;
        for (BranchGraph.Segment segment : graph.segments()) {
            if (segment.id() >= trunkSections && segment.startY() > 0.5) {
                result = Math.min(result, segment.startY());
            }
        }
        if (!Double.isFinite(result)) {
            throw new AssertionError("generated conifer has no crown branches");
        }
        return result;
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

    private record SpeciesModels(
        long seed,
        BranchGraph spruceGraph,
        BranchGraph pineGraph,
        int trunkSections
    ) {
    }
}
