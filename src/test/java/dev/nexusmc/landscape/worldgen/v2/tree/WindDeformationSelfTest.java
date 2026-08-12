package dev.nexusmc.landscape.worldgen.v2.tree;

/** Verifies deterministic exposed-tree deformation and conservative bounds. */
public final class WindDeformationSelfTest {
    private WindDeformationSelfTest() {
    }

    public static void main(String[] args) {
        verifyWindBendsTreeWithoutChangingTopology();
        verifyEnvelopeIncludesWindReserve();
        System.out.println("WindDeformationSelfTest: PASS");
    }

    private static void verifyWindBendsTreeWithoutChangingTopology() {
        long seed = 0x57494E4454524545L;
        TreeEnvironment windy = new TreeEnvironment(
            0.58, 0.48, 0.18, 0.03,
            0.92, 0.24, 0.82, 0.10, 126
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
        BranchGraph first = wind.apply(base, anatomy);
        BranchGraph second = wind.apply(base, anatomy);

        require(wind.strength() > 0.45, "wind response is too weak for exposed tree");
        require(wind.maxLateralShift() > 1.0,
            "hero exposed tree should have visible lateral deformation");
        require(first.fingerprint() == second.fingerprint(),
            "wind deformation is not deterministic");
        require(first.segments().size() == base.segments().size(),
            "wind deformation changed graph topology");

        for (int index = 0; index < base.segments().size(); index++) {
            BranchGraph.Segment before = base.segments().get(index);
            BranchGraph.Segment after = first.segments().get(index);
            require(before.id() == after.id()
                    && before.parentId() == after.parentId()
                    && before.role() == after.role(),
                "wind deformation changed segment identity");
            if (before.role() == SegmentRole.ROOT && before.endY() <= 0.0) {
                require(before.endX() == after.endX()
                        && before.endY() == after.endY()
                        && before.endZ() == after.endZ(),
                    "underground root endpoint moved with wind");
            }
        }

        BranchGraph.Segment baseTop = lastTrunk(base);
        BranchGraph.Segment bentTop = lastTrunk(first);
        double displacementX = bentTop.endX() - baseTop.endX();
        double displacementZ = bentTop.endZ() - baseTop.endZ();
        double projection = displacementX * wind.directionX()
            + displacementZ * wind.directionZ();
        require(projection > 0.45,
            "tree top did not bend along the wind direction");

        CanopyPlan canopy = anatomy.canopy(first);
        VoxelTreeModel model = TreeVoxelizer.voxelize(
            first, TreeQualityTier.HERO, canopy
        );
        require(!model.wood().isEmpty() && !model.leaves().isEmpty(),
            "wind-deformed tree failed voxelization");
        require(model.wood().size() <= TreeQualityTier.HERO.budget().maxWoodBlocks(),
            "wind-deformed wood exceeded budget");
        require(model.leaves().size() <= TreeQualityTier.HERO.budget().maxLeafBlocks(),
            "wind-deformed canopy exceeded budget");
    }

    private static void verifyEnvelopeIncludesWindReserve() {
        TreeEnvironment calm = new TreeEnvironment(
            0.58, 0.48, 0.18, 0.03,
            0.0, 0.0, 0.82, 0.10, 126
        );
        TreeEnvironment windy = new TreeEnvironment(
            0.58, 0.48, 0.18, 0.03,
            0.92, 0.24, 0.82, 0.10, 126
        );
        TreePlacementEnvelope calmEnvelope = TreePlacementEnvelope.estimate(
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.MID,
            calm,
            false
        );
        TreePlacementEnvelope windyEnvelope = TreePlacementEnvelope.estimate(
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.MID,
            windy,
            false
        );
        require(windyEnvelope.horizontalRadius() >= calmEnvelope.horizontalRadius(),
            "windy envelope must not be narrower than calm envelope");
        require(
            WindDeformationPlan.conservativeExtraRadius(TreeQualityTier.MID, windy) > 0,
            "windy environment did not reserve deformation radius"
        );
    }

    private static BranchGraph.Segment lastTrunk(BranchGraph graph) {
        BranchGraph.Segment result = null;
        for (BranchGraph.Segment segment : graph.segments()) {
            if (segment.role() == SegmentRole.TRUNK) {
                result = segment;
            }
        }
        if (result == null) {
            throw new AssertionError("graph has no trunk");
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
