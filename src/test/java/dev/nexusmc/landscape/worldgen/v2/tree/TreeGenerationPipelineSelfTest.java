package dev.nexusmc.landscape.worldgen.v2.tree;

/** Verifies that all callers share one deterministic complete-tree pipeline. */
public final class TreeGenerationPipelineSelfTest {
    private static final VoxelTreeModel.Voxel ORIGIN_WOOD =
        new VoxelTreeModel.Voxel(0, 0, 0);

    private TreeGenerationPipelineSelfTest() {
    }

    public static void main(String[] args) {
        verifyMatrixDeterminismAndCacheParity();
        System.out.println("TreeGenerationPipelineSelfTest: PASS");
    }

    private static void verifyMatrixDeterminismAndCacheParity() {
        TreeEnvironment calm = new TreeEnvironment(
            0.12, 0.66, 0.42, 0.08,
            0.0, 0.0, 0.28, 0.05, 92
        );
        TreeEnvironment windy = new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            -0.92, 0.24, 0.68, -0.12, 138
        );
        TreeEnvironment[] environments = {calm, windy};
        long rootSeed = 0x504950454C494E45L;

        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                for (int environmentIndex = 0;
                     environmentIndex < environments.length;
                     environmentIndex++) {
                    long seed = TreeLifeHistory.mix(
                        rootSeed
                            ^ ((long)species.ordinal() << 56)
                            ^ ((long)quality.ordinal() << 48)
                            ^ environmentIndex * 0x9E3779B97F4A7C15L
                    );
                    TreeEnvironment environment = environments[environmentIndex];
                    ProceduralTreePlan plan = new ProceduralTreePlan(
                        seed,
                        species,
                        quality,
                        environment,
                        quality == TreeQualityTier.HERO
                    );

                    TreeGenerationPipeline.GeneratedTree first =
                        TreeGenerationPipeline.generate(plan);
                    TreeGenerationPipeline.GeneratedTree second =
                        TreeGenerationPipeline.generate(plan);
                    String label = species + "/" + quality + "/env=" + environmentIndex;

                    require(first.anatomy().equals(second.anatomy()),
                        label + " anatomy is not deterministic");
                    require(first.baseGraph().fingerprint()
                            == second.baseGraph().fingerprint(),
                        label + " base graph is not deterministic");
                    require(first.graph().fingerprint() == second.graph().fingerprint(),
                        label + " deformed graph is not deterministic");
                    require(first.canopy().equals(second.canopy()),
                        label + " canopy is not deterministic");
                    require(first.model().fingerprint() == second.model().fingerprint(),
                        label + " voxel model is not deterministic");
                    require(first.baseGraph().segments().size()
                            == first.graph().segments().size(),
                        label + " wind changed topology");
                    require(first.anatomy().species() == species,
                        label + " species was lost in pipeline");
                    require(first.model().quality() == quality,
                        label + " quality was lost in pipeline");
                    require(first.model().wood().contains(ORIGIN_WOOD),
                        label + " generated model is missing trunk base voxel");
                    verifyGroundInteraction(label, first);
                    verifyEnvelopeContains(label, plan.envelope(), first.model());

                    TreeModelCache.clear();
                    TreeModelCache.Lookup lookup = TreeModelCache.getOrCreateDetailed(plan);
                    require(!lookup.cacheHit(), label + " first cache lookup must miss");
                    require(lookup.model().fingerprint() == first.model().fingerprint(),
                        label + " cache miss did not use unified pipeline model");
                    require(lookup.model().wood().contains(ORIGIN_WOOD),
                        label + " cached model lost trunk base voxel");
                    TreeModelCache.Lookup hit = TreeModelCache.getOrCreateDetailed(plan);
                    require(hit.cacheHit(), label + " second cache lookup must hit");
                    require(hit.model().fingerprint() == first.model().fingerprint(),
                        label + " cache hit changed model identity");
                }
            }
        }
        TreeModelCache.clear();
    }

    private static void verifyGroundInteraction(
        String label,
        TreeGenerationPipeline.GeneratedTree generated
    ) {
        require(generated.model().leaves().stream().noneMatch(voxel -> voxel.y() < 0),
            label + " canopy crossed below ground");

        RootPlan roots = generated.anatomy().roots();
        for (RootPlan.RootArm arm : roots.arms()) {
            require(arm.endY() < 0.0,
                label + " root arm does not anchor below ground");
            require(arm.endY() >= -1.05,
                label + " root arm escaped local soil depth: " + arm.endY());
        }
        if (!roots.arms().isEmpty()) {
            require(generated.model().wood().stream().anyMatch(voxel -> voxel.y() < 0),
                label + " planned roots produced no underground wood");
        }
    }

    private static void verifyEnvelopeContains(
        String label,
        TreePlacementEnvelope envelope,
        VoxelTreeModel model
    ) {
        int maxHorizontal = 0;
        int maxY = Integer.MIN_VALUE;
        int minWoodY = Integer.MAX_VALUE;

        for (VoxelTreeModel.Voxel voxel : model.wood()) {
            maxHorizontal = Math.max(
                maxHorizontal,
                Math.max(Math.abs(voxel.x()), Math.abs(voxel.z()))
            );
            maxY = Math.max(maxY, voxel.y());
            minWoodY = Math.min(minWoodY, voxel.y());
        }
        for (VoxelTreeModel.Voxel voxel : model.leaves()) {
            maxHorizontal = Math.max(
                maxHorizontal,
                Math.max(Math.abs(voxel.x()), Math.abs(voxel.z()))
            );
            maxY = Math.max(maxY, voxel.y());
        }

        require(maxHorizontal <= envelope.horizontalRadius(),
            label + " model escaped envelope radius: model=" + maxHorizontal
                + " envelope=" + envelope.horizontalRadius());
        require(maxY <= envelope.height(),
            label + " model escaped envelope height: model=" + maxY
                + " envelope=" + envelope.height());
        require(-minWoodY <= envelope.undergroundDepth(),
            label + " model escaped envelope underground depth: model="
                + (-minWoodY) + " envelope=" + envelope.undergroundDepth());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
