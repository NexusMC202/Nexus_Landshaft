package dev.nexusmc.landscape.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;

final class CaveFeatureSupport {
    private CaveFeatureSupport() {
    }

    static BlockPos findFloor(WorldGenLevel level, BlockPos origin, int range) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        int minY = Math.max(level.getMinBuildHeight() + 5, origin.getY() - range);
        int maxY = Math.min(level.getMaxBuildHeight() - 12, origin.getY() + range);
        for (int y = maxY; y >= minY; y--) {
            cursor.setY(y);
            if (level.isEmptyBlock(cursor)
                && level.isEmptyBlock(cursor.above())
                && level.getBlockState(cursor.below()).isFaceSturdy(level, cursor.below(), Direction.UP)) {
                return cursor.below().immutable();
            }
        }
        return null;
    }

    static boolean hasRoom(WorldGenLevel level, BlockPos floor, int radius, int height) {
        int open = 0;
        int total = 0;
        for (int y = 2; y <= height; y += 3) {
            for (int x = -radius; x <= radius; x += Math.max(2, radius)) {
                for (int z = -radius; z <= radius; z += Math.max(2, radius)) {
                    total++;
                    if (level.isEmptyBlock(floor.offset(x, y, z))) {
                        open++;
                    }
                }
            }
        }
        return total > 0 && open * 100 / total >= 65;
    }

    static BlockPos ceilingAbove(WorldGenLevel level, BlockPos floor, int maxHeight) {
        for (int y = 3; y <= maxHeight; y++) {
            BlockPos pos = floor.above(y);
            if (!level.isEmptyBlock(pos)) {
                return pos;
            }
        }
        return null;
    }
}
