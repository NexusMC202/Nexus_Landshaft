package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.HashMap;
import java.util.Map;

/** Verifies that structural segment roles replace the old root-as-dead encoding. */
public final class SegmentRoleSelfTest {
    private SegmentRoleSelfTest() {
    }

    public static void main(String[] args) {
        verifyGeneratedRoles();
        verifyCanopyUsesOnlyFoliageRoles();
        verifyCompatibilityBooleanMapping();
        System.out.println("SegmentRoleSelfTest: PASS");
    }

    private static void verifyGeneratedRoles() {
        long seed = 0x5345474D454E5452L;
        TreeEnvironment exposed = new TreeEnvironment(
            0.58, 0.57, 0.16, 0.04,
            -0.82, 0.30, 0.78, -0.20, 116
        );
        AnatomyPlan anatomy = AnatomyPlan.resolve(
            seed,
            ConiferSpeciesProfile.SPRUCE,
            TreeQualityTier.HERO,
            exposed
        );
        BranchGraph graph = ConiferBranchGenerator.generate(anatomy);

        int trunks = 0;
        int roots = 0;
        for (BranchGraph.Segment segment : graph.segments()) {
            if (segment.role() == SegmentRole.TRUNK) {
                trunks++;
            }
            if (segment.role() == SegmentRole.ROOT) {
                roots++;
                require(!segment.dead(), "root must not be classified as dead wood");
                require(segment.endY() < segment.startY(),
                    "root must descend from its attachment");
            }
            require(segment.dead() == (segment.role() == SegmentRole.DEAD_BRANCH),
                "dead compatibility accessor disagrees with segment role");
        }

        require(trunks == anatomy.trunk().sections(),
            "trunk role count does not match trunk plan sections");
        require(roots == anatomy.roots().arms().size(),
            "root role count does not match root plan arms");
        require(roots > 0, "exposed HERO tree must contain explicit root segments");
    }

    private static void verifyCanopyUsesOnlyFoliageRoles() {
        long seed = 0x43414E4F5059524FL;
        TreeEnvironment environment = new TreeEnvironment(
            0.26, 0.65, 0.42, 0.07,
            -0.44, 0.18, 0.62, -0.12, 102
        );
        AnatomyPlan anatomy = AnatomyPlan.resolve(
            seed,
            ConiferSpeciesProfile.PINE,
            TreeQualityTier.HERO,
            environment
        );
        BranchGraph graph = ConiferBranchGenerator.generate(anatomy);
        CanopyPlan canopy = anatomy.canopy(graph);

        Map<Integer, SegmentRole> roles = new HashMap<>();
        for (BranchGraph.Segment segment : graph.segments()) {
            roles.put(segment.id(), segment.role());
        }
        for (CanopyPlan.Cluster cluster : canopy.clusters()) {
            SegmentRole role = roles.get(cluster.sourceSegmentId());
            require(role != null, "canopy references unknown source segment");
            require(role.canCarryFoliage(),
                "canopy cluster originated from non-foliage role: " + role);
            require(role != SegmentRole.ROOT,
                "root segment generated a canopy cluster");
            require(role != SegmentRole.DEAD_BRANCH,
                "dead branch generated a canopy cluster");
        }
    }

    private static void verifyCompatibilityBooleanMapping() {
        BranchGraph.Segment live = new BranchGraph.Segment(
            1, 0,
            0.0, 1.0, 0.0,
            1.0, 1.0, 0.0,
            0.30, 0.10,
            false
        );
        BranchGraph.Segment dead = new BranchGraph.Segment(
            2, 0,
            0.0, 2.0, 0.0,
            1.0, 2.0, 0.0,
            0.30, 0.0,
            true
        );
        require(live.role() == SegmentRole.LIVE_BRANCH && !live.dead(),
            "legacy false mapping must resolve to LIVE_BRANCH");
        require(dead.role() == SegmentRole.DEAD_BRANCH && dead.dead(),
            "legacy true mapping must resolve to DEAD_BRANCH");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
