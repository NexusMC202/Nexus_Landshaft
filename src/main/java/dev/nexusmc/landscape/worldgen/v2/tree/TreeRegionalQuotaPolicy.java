package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Order-independent regional admission for expensive tree tiers. The policy
 * selects deterministic 16x16 candidate cells inside a 128x128 block region,
 * so chunk generation order and parallel execution cannot change the result.
 */
public final class TreeRegionalQuotaPolicy {
    static final int CELL_SIZE = 16;
    static final int REGION_CELLS = 8;
    static final int REGION_SIZE = CELL_SIZE * REGION_CELLS;

    private static final long MID_SALT = 0x4D49445F51554F54L;
    private static final long HERO_SALT = 0x4845524F5F51554FL;

    private TreeRegionalQuotaPolicy() {
    }

    public static boolean allows(
        long worldSeed,
        int blockX,
        int blockZ,
        TreeQualityTier quality
    ) {
        if (quality == null) {
            throw new IllegalArgumentException("quality is required");
        }
        if (quality == TreeQualityTier.BASIC) {
            return true;
        }

        int regionX = Math.floorDiv(blockX, REGION_SIZE);
        int regionZ = Math.floorDiv(blockZ, REGION_SIZE);
        int cellX = Math.floorMod(Math.floorDiv(blockX, CELL_SIZE), REGION_CELLS);
        int cellZ = Math.floorMod(Math.floorDiv(blockZ, CELL_SIZE), REGION_CELLS);
        int cellIndex = cellZ * REGION_CELLS + cellX;

        int quota = quality == TreeQualityTier.HERO ? 1 : 8;
        long salt = quality == TreeQualityTier.HERO ? HERO_SALT : MID_SALT;
        return rank(worldSeed, regionX, regionZ, cellIndex, salt) < quota;
    }

    static int rank(
        long worldSeed,
        int regionX,
        int regionZ,
        int targetCell,
        long salt
    ) {
        long targetScore = score(worldSeed, regionX, regionZ, targetCell, salt);
        int rank = 0;
        for (int cell = 0; cell < REGION_CELLS * REGION_CELLS; cell++) {
            if (cell == targetCell) {
                continue;
            }
            long otherScore = score(worldSeed, regionX, regionZ, cell, salt);
            if (Long.compareUnsigned(otherScore, targetScore) < 0
                || (otherScore == targetScore && cell < targetCell)) {
                rank++;
            }
        }
        return rank;
    }

    private static long score(
        long worldSeed,
        int regionX,
        int regionZ,
        int cell,
        long salt
    ) {
        long value = worldSeed ^ salt;
        value ^= (long)regionX * 0x632BE59BD9B4E019L;
        value ^= (long)regionZ * 0x94D049BB133111EBL;
        value ^= (long)cell * 0x9E3779B97F4A7C15L;
        return TreeLifeHistory.mix(value);
    }
}
