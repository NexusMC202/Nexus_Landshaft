package dev.nexusmc.landscape.worldgen.v2.tree;

import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TreeModelFoundationSelfTest {
    private TreeModelFoundationSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyBudgets();
        verifyEnvironment();
        verifyGraph();
        verifyVoxelModel();
        verifyLifeHistory();
        verifyConiferGenerator();
        verifyVoxelizer();
        verifyRuntimePolicy();
        TreePreviewExport.main(new String[0]);
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
        TreeEnvironment environment = environment();
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

    private static void verifyLifeHistory() {
        TreeEnvironment environment = environment();
        TreeLifeHistory first = TreeLifeHistory.generate(
            918273645L, TreeQualityTier.MID, environment
        );
        TreeLifeHistory second = TreeLifeHistory.generate(
            918273645L, TreeQualityTier.MID, environment
        );
        TreeLifeHistory different = TreeLifeHistory.generate(
            918273646L, TreeQualityTier.MID, environment
        );
        require(first.equals(second), "life history is not deterministic");
        require(!first.equals(different), "different seed did not change history");
        require(first.ageYears() >= 42, "MID tree age below tier range");
    }

    private static void verifyConiferGenerator() {
        TreeEnvironment environment = environment();
        for (TreeQualityTier quality : TreeQualityTier.values()) {
            TreeLifeHistory history = TreeLifeHistory.generate(
                0x51A7C0DEL, quality, environment
            );
            BranchGraph first = ConiferBranchGenerator.generate(
                0x51A7C0DEL, quality, environment, history
            );
            BranchGraph second = ConiferBranchGenerator.generate(
                0x51A7C0DEL, quality, environment, history
            );
            BranchGraph different = ConiferBranchGenerator.generate(
                0x51A7C0DFL,
                quality,
                environment,
                TreeLifeHistory.generate(0x51A7C0DFL, quality, environment)
            );
            require(first.fingerprint() == second.fingerprint(),
                quality + " conifer graph is not deterministic");
            require(first.fingerprint() != different.fingerprint(),
                quality + " conifer graph ignores seed");
            require(first.segments().size() <= quality.budget().maxBranchSegments(),
                quality + " conifer exceeds branch budget");
            require(first.segments().size() >= 5,
                quality + " conifer skeleton is too simple");
            require(first.segments().getFirst().parentId() == -1,
                quality + " conifer root is invalid");
        }
    }

    private static void verifyVoxelizer() {
        TreeEnvironment environment = environment();
        for (TreeQualityTier quality : TreeQualityTier.values()) {
            long seed = 0x7A11C0DEL + quality.ordinal();
            TreeLifeHistory history = TreeLifeHistory.generate(
                seed, quality, environment
            );
            BranchGraph graph = ConiferBranchGenerator.generate(
                seed, quality, environment, history
            );
            VoxelTreeModel first = TreeVoxelizer.voxelize(seed, quality, graph);
            VoxelTreeModel second = TreeVoxelizer.voxelize(seed, quality, graph);
            require(first.fingerprint() == second.fingerprint(),
                quality + " voxelizer is not deterministic");
            require(!first.leaves().isEmpty(),
                quality + " conifer has no foliage");
            require(first.wood().size() <= quality.budget().maxWoodBlocks(),
                quality + " wood budget exceeded");
            require(first.leaves().size() <= quality.budget().maxLeafBlocks(),
                quality + " leaf budget exceeded");
            require(connected(first.wood()),
                quality + " wood is not a connected structure");
            Set<VoxelTreeModel.Voxel> wood = new HashSet<>(first.wood());
            for (VoxelTreeModel.Voxel leaf : first.leaves()) {
                require(!wood.contains(leaf),
                    quality + " leaf overlaps wood: " + leaf);
            }
        }
    }

    private static void verifyRuntimePolicy() {
        require(ProceduralTreePolicy.supports(
            VegetationProfile.TreeShape.SPRUCE_CONICAL
        ), "spruce must use Tree System v2");
        require(ProceduralTreePolicy.supports(
            VegetationProfile.TreeShape.PINE_TALL
        ), "pine must use Tree System v2");
        require(!ProceduralTreePolicy.supports(
            VegetationProfile.TreeShape.OAK_ROUNDED
        ), "oak rollout is premature");

        TreeEnvironment basic = ProceduralTreePolicy.environment(
            0.08, 0.45, 0.72, 0.06,
            0.06, 0.02, 0.10, 0.04, 84
        );
        TreeEnvironment exposed = ProceduralTreePolicy.environment(
            0.58, 0.40, 0.18, 0.02,
            -0.82, 0.20, 0.70, -0.10, 132
        );
        TreeEnvironment hero = ProceduralTreePolicy.environment(
            0.12, 0.82, 0.28, 0.12,
            0.12, -0.08, 0.82, 0.14, 92
        );

        require(ProceduralTreePolicy.quality(false, 0.08, basic)
            == TreeQualityTier.BASIC, "ordinary conifer must remain BASIC");
        require(ProceduralTreePolicy.quality(false, 0.18, exposed)
            == TreeQualityTier.MID, "exposed conifer must become MID");
        require(ProceduralTreePolicy.quality(true, 0.62, hero)
            == TreeQualityTier.HERO, "rare old-growth conifer must become HERO");
        expectFailure(() -> ProceduralTreePolicy.quality(false, 1.2, basic));
    }

    private static boolean connected(List<VoxelTreeModel.Voxel> voxels) {
        Set<VoxelTreeModel.Voxel> remaining = new HashSet<>(voxels);
        ArrayDeque<VoxelTreeModel.Voxel> queue = new ArrayDeque<>();
        VoxelTreeModel.Voxel first = voxels.getFirst();
        remaining.remove(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            VoxelTreeModel.Voxel current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        VoxelTreeModel.Voxel neighbor = new VoxelTreeModel.Voxel(
                            current.x() + dx,
                            current.y() + dy,
                            current.z() + dz
                        );
                        if (remaining.remove(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return remaining.isEmpty();
    }

    private static TreeEnvironment environment() {
        return new TreeEnvironment(
            0.2, 0.7, 0.5, 0.4,
            -0.6, 0.2, 0.8, -0.1, 96
        );
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
