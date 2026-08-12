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
        TreeEnvironment windy = new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            -0.92, 0.24, 0.68, -0.12, 138
        );
        ProceduralTreePlan procedural = new ProceduralTreePlan(
            seed,
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.HERO,
            windy,
            true
        );
        AnatomyPlan anatomy = AnatomyPlan.resolve(procedural);
        BranchGraph base = SpeciesConiferBranchGenerator.generate(anatomy);
        WindDeformationPlan wind = WindDeformationPlan.resolve(anatomy);
        BranchGraph graph = wind.apply(base, anatomy);
        CanopyPlan canopy = anatomy.canopy(graph);
        VoxelTreeModel first = TreeVoxelizer.voxelize(
            graph, TreeQualityTier.HERO, canopy
        );
        VoxelTreeModel second = TreeVoxelizer.voxelize(
            graph, TreeQualityTier.HERO, canopy
        );
        CrownBiasMetrics a = CrownBiasMetrics.measure(first, windy);
        CrownBiasMetrics b = CrownBiasMetrics.measure(second, windy);
        require(a.equals(b), "generated crown bias is not deterministic");
        require(a.leafCount() == first.leaves().size(),
            "generated crown leaf count mismatch");
        require(a.sideLeaves() > 0, "windy generated crown has no directional leaves");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
