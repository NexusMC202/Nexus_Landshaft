package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.EnumMap;
import java.util.Map;

/** Verifies the staged anatomy decisions introduced by the species technique. */
public final class AnatomicalPlanningSelfTest {
    private AnatomicalPlanningSelfTest() {
    }

    public static void main(String[] args) {
        verifyDenseForestStretchesTrunk();
        verifyExposureStrengthensRoots();
        verifyBranchFamiliesAreDistinct();
        verifyPlanningIsDeterministic();
        System.out.println("AnatomicalPlanningSelfTest: PASS");
    }

    private static void verifyDenseForestStretchesTrunk() {
        long seed = 0x71A6A5EEDL;
        TreeEnvironment dense = new TreeEnvironment(
            0.10, 0.66, 0.92, 0.06,
            0.08, 0.03, 0.08, 0.04, 88
        );
        TreeEnvironment open = new TreeEnvironment(
            0.10, 0.66, 0.12, 0.06,
            0.08, 0.03, 0.88, 0.10, 88
        );
        TreeLifeHistory denseHistory = TreeLifeHistory.generate(
            seed, TreeQualityTier.MID, dense
        );
        TreeLifeHistory openHistory = TreeLifeHistory.generate(
            seed, TreeQualityTier.MID, open
        );
        TrunkPlan denseTrunk = TrunkPlan.resolve(
            seed, TreeQualityTier.MID, dense, denseHistory
        );
        TrunkPlan openTrunk = TrunkPlan.resolve(
            seed, TreeQualityTier.MID, open, openHistory
        );

        require(denseTrunk.height() > openTrunk.height(),
            "dense forest must stretch a conifer upward");
        require(denseTrunk.crownStart() > openTrunk.crownStart(),
            "dense forest must raise the crown start");
        require(denseTrunk.baseRadius() < openTrunk.baseRadius(),
            "open conifer should have a broader base");
    }

    private static void verifyExposureStrengthensRoots() {
        long seed = 0x524F4F745L;
        TreeEnvironment sheltered = new TreeEnvironment(
            0.05, 0.64, 0.86, 0.04,
            0.04, 0.02, 0.10, 0.06, 82
        );
        TreeEnvironment exposed = new TreeEnvironment(
            0.62, 0.52, 0.18, 0.02,
            -0.86, 0.28, 0.74, -0.18, 124
        );
        TreeLifeHistory shelteredHistory = TreeLifeHistory.generate(
            seed, TreeQualityTier.HERO, sheltered
        );
        TreeLifeHistory exposedHistory = TreeLifeHistory.generate(
            seed, TreeQualityTier.HERO, exposed
        );
        TrunkPlan shelteredTrunk = TrunkPlan.resolve(
            seed, TreeQualityTier.HERO, sheltered, shelteredHistory
        );
        TrunkPlan exposedTrunk = TrunkPlan.resolve(
            seed, TreeQualityTier.HERO, exposed, exposedHistory
        );
        RootPlan shelteredRoots = RootPlan.resolve(
            seed, TreeQualityTier.HERO, sheltered, shelteredHistory,
            shelteredTrunk
        );
        RootPlan exposedRoots = RootPlan.resolve(
            seed, TreeQualityTier.HERO, exposed, exposedHistory,
            exposedTrunk
        );

        require(exposedRoots.arms().size() >= shelteredRoots.arms().size(),
            "exposed tree must not have fewer roots");
        require(totalRootReach(exposedRoots) > totalRootReach(shelteredRoots),
            "exposed tree roots must reach farther");
    }

    private static void verifyBranchFamiliesAreDistinct() {
        long seed = 0xB2A6C4F1L;
        TreeEnvironment environment = new TreeEnvironment(
            0.18, 0.70, 0.44, 0.08,
            -0.45, 0.22, 0.66, -0.10, 96
        );
        TreeLifeHistory history = TreeLifeHistory.generate(
            seed, TreeQualityTier.HERO, environment
        );
        TrunkPlan trunk = TrunkPlan.resolve(
            seed, TreeQualityTier.HERO, environment, history
        );
        BranchFamilyPlan plan = BranchFamilyPlan.resolve(
            seed, TreeQualityTier.HERO, environment, history, trunk
        );

        Map<BranchFamilyPlan.Kind, BranchFamilyPlan.Family> byKind =
            new EnumMap<>(BranchFamilyPlan.Kind.class);
        for (BranchFamilyPlan.Family family : plan.families()) {
            require(byKind.put(family.kind(), family) == null,
                "duplicate branch family: " + family.kind());
        }
        require(byKind.size() == BranchFamilyPlan.Kind.values().length,
            "not every branch family was planned");

        BranchFamilyPlan.Family lower = byKind.get(
            BranchFamilyPlan.Kind.LOWER_SPARSE
        );
        BranchFamilyPlan.Family middle = byKind.get(
            BranchFamilyPlan.Kind.MIDDLE_STRUCTURAL
        );
        BranchFamilyPlan.Family upper = byKind.get(
            BranchFamilyPlan.Kind.UPPER_SHORT
        );
        BranchFamilyPlan.Family dead = byKind.get(
            BranchFamilyPlan.Kind.DEAD_AND_DAMAGED
        );

        require(middle.baseLength() > upper.baseLength(),
            "structural middle branches must be longer than upper branches");
        require(lower.droop() > upper.droop(),
            "lower branches must droop more than upper branches");
        require(!dead.foliageBearing() && dead.deadProbability() == 1.0,
            "dead family must never carry foliage");
        require(upper.minVertical() > lower.minVertical(),
            "upper family must occupy a higher band");
    }

    private static void verifyPlanningIsDeterministic() {
        long seed = 912345678L;
        TreeEnvironment environment = new TreeEnvironment(
            0.24, 0.72, 0.51, 0.10,
            -0.52, 0.18, 0.60, -0.14, 101
        );
        TreeLifeHistory history = TreeLifeHistory.generate(
            seed, TreeQualityTier.MID, environment
        );
        TrunkPlan firstTrunk = TrunkPlan.resolve(
            seed, TreeQualityTier.MID, environment, history
        );
        TrunkPlan secondTrunk = TrunkPlan.resolve(
            seed, TreeQualityTier.MID, environment, history
        );
        require(firstTrunk.equals(secondTrunk),
            "trunk planning is not deterministic");

        RootPlan firstRoots = RootPlan.resolve(
            seed, TreeQualityTier.MID, environment, history, firstTrunk
        );
        RootPlan secondRoots = RootPlan.resolve(
            seed, TreeQualityTier.MID, environment, history, secondTrunk
        );
        require(firstRoots.equals(secondRoots),
            "root planning is not deterministic");

        BranchFamilyPlan firstBranches = BranchFamilyPlan.resolve(
            seed, TreeQualityTier.MID, environment, history, firstTrunk
        );
        BranchFamilyPlan secondBranches = BranchFamilyPlan.resolve(
            seed, TreeQualityTier.MID, environment, history, secondTrunk
        );
        require(firstBranches.equals(secondBranches),
            "branch-family planning is not deterministic");
    }

    private static double totalRootReach(RootPlan plan) {
        double total = 0.0;
        for (RootPlan.RootArm arm : plan.arms()) {
            total += Math.sqrt(arm.endX() * arm.endX() + arm.endZ() * arm.endZ());
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
