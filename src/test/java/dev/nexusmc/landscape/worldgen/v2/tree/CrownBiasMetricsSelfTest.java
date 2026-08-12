package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.List;

/** Verifies crown center-of-mass and directional wind-side measurements. */
public final class CrownBiasMetricsSelfTest {
    private CrownBiasMetricsSelfTest() {
    }

    public static void main(String[] args) {
        verifySyntheticDirectionalBias();
        verifyCalmCrownIsNeutral();
        verifyGeneratedWindyMetricsAreDeterministic();
        verifySpeciesQualityWindMatrix();
        System.out.println("CrownBiasMetricsSelfTest: PASS");
    }

    private static void verifySyntheticDirectionalBias() {
        VoxelTreeModel model = new VoxelTreeModel(
            TreeQualityTier.BASIC,
            List.of(new VoxelTreeModel.Voxel(0, 0, 0)),
            List.of(
                new VoxelTreeModel.Voxel(3, 4, 0),
                new VoxelTreeModel.Voxel(2, 5, 1),
                new VoxelTreeModel.Voxel(2, 5, -1),
                new VoxelTreeModel.Voxel(-1, 4, 0),
                new VoxelTreeModel.Voxel(0, 6, 0)
            )
        );
        TreeEnvironment windEast = new TreeEnvironment(
            0.1, 0.5, 0.2, 0.0,
            1.0, 0.0, 0.4, 0.0, 80
        );
        CrownBiasMetrics metrics = CrownBiasMetrics.measure(model, windEast);
        require(metrics.leafCount() == 5, "leaf count mismatch");
        require(metrics.leewardLeaves() == 3, "leeward count mismatch");
        require(metrics.windwardLeaves() == 1, "windward count mismatch");
        require(metrics.neutralLeaves() == 1, "neutral count mismatch");
        require(metrics.centerX() > 1.0, "crown center should be displaced east");
        require(metrics.windProjection() > 1.0,
            "wind projection should be positive for leeward crown");
        require(metrics.directionalBalance() > 0.0,
            "directional balance should favor leeward side");
    }

    private static void verifyCalmCrownIsNeutral() {
        VoxelTreeModel model = new VoxelTreeModel(
            TreeQualityTier.BASIC,
            List.of(new VoxelTreeModel.Voxel(0, 0, 0)),
            List.of(
                new VoxelTreeModel.Voxel(2, 4, 0),
                new VoxelTreeModel.Voxel(-2, 4, 0)
            )
        );
        TreeEnvironment calm = new TreeEnvironment(
            0.1, 0.5, 0.2, 0.0,
            0.0, 0.0, 0.4, 0.0, 80
        );
        CrownBiasMetrics metrics = CrownBiasMetrics.measure(model, calm);
        require(metrics.neutralLeaves() == 2, "calm leaves must be neutral");
        require(metrics.sideLeaves() == 0, "calm crown must have no wind sides");
        require(metrics.windProjection() == 0.0,
            "calm crown wind projection must be zero");
    }

    private static void verifyGeneratedWindyMetricsAreDeterministic() {
        long seed = 0x43524F574E424941L;
        TreeEnvironment windy = windyEnvironment();
        ProceduralTreePlan procedural = new ProceduralTreePlan(
            seed,
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.HERO,
            windy,
            true
        );
        VoxelTreeModel first = generate(procedural);
        VoxelTreeModel second = generate(procedural);
        CrownBiasMetrics a = CrownBiasMetrics.measure(first, windy);
        CrownBiasMetrics b = CrownBiasMetrics.measure(second, windy);
        require(a.equals(b), "generated crown bias is not deterministic");
        require(a.leafCount() == first.leaves().size(),
            "generated crown leaf count mismatch");
        require(a.sideLeaves() > 0, "windy generated crown has no directional leaves");
    }

    private static void verifySpeciesQualityWindMatrix() {
        int index = 0;
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                long seed = 0x57494E444D415452L
                    + index * 0x9E3779B97F4A7C15L;
                TreeEnvironment windy = windyEnvironment();
                TreeEnvironment calm = calmEnvironment();
                ProceduralTreePlan windyPlan = new ProceduralTreePlan(
                    seed,
                    species,
                    quality,
                    windy,
                    quality == TreeQualityTier.HERO
                );
                ProceduralTreePlan calmPlan = new ProceduralTreePlan(
                    seed,
                    species,
                    quality,
                    calm,
                    quality == TreeQualityTier.HERO
                );

                VoxelTreeModel windyFirst = generate(windyPlan);
                VoxelTreeModel windySecond = generate(windyPlan);
                VoxelTreeModel calmModel = generate(calmPlan);
                CrownBiasMetrics windyMetrics = CrownBiasMetrics.measure(
                    windyFirst, windy
                );
                CrownBiasMetrics calmMetrics = CrownBiasMetrics.measure(
                    calmModel, calm
                );

                String label = species + " " + quality;
                require(windyFirst.fingerprint() == windySecond.fingerprint(),
                    label + " windy model is not deterministic");
                require(windyFirst.fingerprint() != calmModel.fingerprint(),
                    label + " windy model collapsed to calm geometry");
                require(windyMetrics.leafCount() == windyFirst.leaves().size(),
                    label + " windy crown leaf count mismatch");
                require(windyMetrics.sideLeaves() > 0,
                    label + " windy crown has no directional leaves");
                require(Double.isFinite(windyMetrics.windProjection()),
                    label + " windy projection is not finite");
                require(Double.isFinite(windyMetrics.directionalBalance()),
                    label + " windy balance is not finite");
                require(calmMetrics.sideLeaves() == 0,
                    label + " calm crown has directional leaves");
                require(calmMetrics.neutralLeaves() == calmMetrics.leafCount(),
                    label + " calm crown is not fully neutral");
                require(calmMetrics.windProjection() == 0.0,
                    label + " calm wind projection must be zero");
                index++;
            }
        }
    }

    private static VoxelTreeModel generate(ProceduralTreePlan procedural) {
        AnatomyPlan anatomy = AnatomyPlan.resolve(procedural);
        BranchGraph base = SpeciesConiferBranchGenerator.generate(anatomy);
        WindDeformationPlan wind = WindDeformationPlan.resolve(anatomy);
        BranchGraph graph = wind.apply(base, anatomy);
        CanopyPlan canopy = anatomy.canopy(graph);
        return TreeVoxelizer.voxelize(graph, procedural.quality(), canopy);
    }

    private static TreeEnvironment windyEnvironment() {
        return new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            -0.92, 0.24, 0.68, -0.12, 138
        );
    }

    private static TreeEnvironment calmEnvironment() {
        return new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            0.0, 0.0, 0.68, -0.12, 138
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
