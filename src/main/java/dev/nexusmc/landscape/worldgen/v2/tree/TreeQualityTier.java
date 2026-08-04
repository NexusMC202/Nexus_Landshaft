package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Visual/detail tier for procedural trees. Budgets are deliberately hard
 * limits so a beautiful tree can never become an unbounded worldgen task.
 */
public enum TreeQualityTier {
    BASIC(new TreeBudget(96, 420, 20, 12, 10)),
    MID(new TreeBudget(240, 1_200, 44, 22, 18)),
    HERO(new TreeBudget(640, 3_600, 96, 42, 34));

    private final TreeBudget budget;

    TreeQualityTier(TreeBudget budget) {
        this.budget = budget;
    }

    public TreeBudget budget() {
        return budget;
    }

    public record TreeBudget(
        int maxWoodBlocks,
        int maxLeafBlocks,
        int maxBranchSegments,
        int maxHorizontalRadius,
        int maxHeight
    ) {
        public TreeBudget {
            if (maxWoodBlocks <= 0 || maxLeafBlocks <= 0
                || maxBranchSegments <= 0 || maxHorizontalRadius <= 0
                || maxHeight <= 0) {
                throw new IllegalArgumentException("tree budgets must be positive");
            }
        }

        public int maxTotalBlocks() {
            return Math.addExact(maxWoodBlocks, maxLeafBlocks);
        }
    }
}
