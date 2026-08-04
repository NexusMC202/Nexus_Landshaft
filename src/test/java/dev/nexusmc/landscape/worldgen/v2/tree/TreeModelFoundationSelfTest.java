package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.List;

public final class TreeModelFoundationSelfTest {
    private TreeModelFoundationSelfTest() {
    }

    public static void main(String[] args) {
        verifyBudgets();
        verifyEnvironment();
        verifyGraph();
        verifyVoxelModel();
        System.out.println("TreeModelFoundationSelfTest: PASS");
    }

    private static void verifyBudgets() {
        require(
            TreeQualityTier.BASIC.budget().maxTotalBlocks()
                < TreeQualityTier.MID.budget().maxTotalBlocks(),
            "BASIC must be cheaper than MID"
        );
        require(
            TreeQualityTier.MID.budget().maxTotalBlocks()
                < TreeQualityTier.HERO.budget().maxTotalBlocks(),
            "MID must be cheaper than HERO"
        );
    }

    private static void verifyEnvironment() {
        TreeEnvironment environment = new TreeEnvironment(
            0.2, 0.7, 0.5, 0.4,
            -0.6, 0.2, 0.8, -0.1, 96
        );
        require(environment.windStrength() > 0.6, "wind strength mismatch");
        expectFailure(() -> new TreeEnvironment(
            1.1, 0.5, 0.5, 0.5, 0, 0, 0, 0, 64
        ));
    }

    private static void verifyGraph() {
        BranchGraph graph = new BranchGraph(List.of(
            new BranchGraph.Segment(
                0, -1, 0, 0, 0, 0, 8, 0, 1.4, 0.8, false
            ),
            new BranchGraph.Segment(
                1, 0, 0, 5, 0, 4, 8, 1, 0.7, 0.2, false
            ),
            new BranchGraph.Segment(
                2, 0, 0, 6, 0, -3, 9, -2, 0.6, 0.0, true
            )
        ));
        BranchGraph identical = new BranchGraph(graph.segments());
        require(graph.fingerprint() == identical.fingerprint(),
            "branch fingerprint is not deterministic");
        require(graph.childrenOf(0).size() == 2, "branch children mismatch");
        expectFailure(() -> new BranchGraph(List.of(
            new BranchGraph.Segment(
                0, -1, 0, 0, 0, 0, 4, 0, 1, 0.5, false
            ),
            new BranchGraph.Segment(
                1, 99, 0, 2, 0, 1, 3, 0, 0.4, 0.1, false
            )
        )));
    }

    private static void verifyVoxelModel() {
        VoxelTreeModel model = new VoxelTreeModel(
            TreeQualityTier.BASIC,
            List.of(
                new VoxelTreeModel.Voxel(0, 0, 0),
                new VoxelTreeModel.Voxel(0, 1, 0),
                new VoxelTreeModel.Voxel(0, 2, 0)
            ),
            List.of(
                new VoxelTreeModel.Voxel(1, 2, 0),
                new VoxelTreeModel.Voxel(-1, 2, 0),
                new VoxelTreeModel.Voxel(0, 3, 0)
            )
        );
        VoxelTreeModel identical = new VoxelTreeModel(
            model.quality(), model.wood(), model.leaves()
        );
        require(model.totalBlocks() == 6, "tree block count mismatch");
        require(model.fingerprint() == identical.fingerprint(),
            "voxel fingerprint is not deterministic");
        expectFailure(() -> new VoxelTreeModel(
            TreeQualityTier.BASIC,
            List.of(new VoxelTreeModel.Voxel(0, 0, 0)),
            List.of(new VoxelTreeModel.Voxel(0, 0, 0))
        ));
        expectFailure(() -> new VoxelTreeModel(
            TreeQualityTier.BASIC,
            List.of(new VoxelTreeModel.Voxel(50, 0, 0)),
            List.of()
        ));
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
