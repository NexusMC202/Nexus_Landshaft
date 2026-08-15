package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.List;

/** Applies species modifiers once, before graph generation. */
public record ConiferSpeciesAnatomy(
    TrunkPlan trunk,
    RootPlan roots,
    BranchFamilyPlan branches
) {
    public ConiferSpeciesAnatomy {
        if (trunk == null || roots == null || branches == null) {
            throw new IllegalArgumentException("complete conifer anatomy is required");
        }
    }

    public static ConiferSpeciesAnatomy resolve(
        long seed,
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history
    ) {
        if (species == null) {
            throw new IllegalArgumentException("conifer species is required");
        }
        TrunkPlan base = TrunkPlan.resolve(seed, quality, environment, history);
        int height = clampInt(
            (int)Math.round(base.height() * species.heightMultiplier()),
            5,
            quality.budget().maxHeight()
        );
        int sections = Math.min(base.sections(), height);
        TrunkPlan trunk = new TrunkPlan(
            height,
            sections,
            base.baseRadius() * species.trunkRadiusMultiplier(),
            clamp(base.crownStart() + species.crownStartOffset(), 0.16, 0.78),
            base.leanX() * species.heightMultiplier(),
            base.leanZ() * species.heightMultiplier(),
            base.secondaryLeader(),
            base.secondaryLeaderStart()
        );
        RootPlan roots = RootPlan.resolve(
            seed, quality, environment, history, trunk
        );
        BranchFamilyPlan baseBranches = BranchFamilyPlan.resolve(
            seed, quality, environment, history, trunk
        );
        List<BranchFamilyPlan.Family> families = new ArrayList<>();
        for (BranchFamilyPlan.Family family : baseBranches.families()) {
            double countMultiplier = switch (family.kind()) {
                case LOWER_SPARSE -> species.lowerBranchMultiplier();
                case UPPER_SHORT -> species.upperCrownMultiplier();
                default -> 1.0;
            };
            int count = clampInt(
                (int)Math.round(family.targetCount() * countMultiplier),
                0,
                64
            );
            families.add(new BranchFamilyPlan.Family(
                family.kind(),
                family.minVertical(),
                family.maxVertical(),
                count,
                family.baseLength() * species.branchLengthMultiplier(),
                family.upwardLift(),
                family.droop(),
                family.phase(),
                family.foliageBearing(),
                family.deadProbability()
            ));
        }
        return new ConiferSpeciesAnatomy(
            trunk,
            roots,
            new BranchFamilyPlan(families)
        );
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
