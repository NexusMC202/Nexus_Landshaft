package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class CaveSanctumFeature extends Feature<NoneFeatureConfiguration> {
    public CaveSanctumFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos floor = findCaveFloor(level, context.origin());
        if (floor == null || !hasChamber(level, floor)) {
            return false;
        }

        int trunkHeight = 8 + random.nextInt(7);
        prepareGarden(level, floor, random);
        growTree(level, floor.above(), trunkHeight, random);
        carveShallowOculus(level, floor.above(trunkHeight / 2));
        return true;
    }

    private static BlockPos findCaveFloor(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        int minY = Math.max(level.getMinBuildHeight() + 6, origin.getY() - 24);
        int maxY = Math.min(origin.getY() + 24, level.getMaxBuildHeight() - 16);
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

    private static boolean hasChamber(WorldGenLevel level, BlockPos floor) {
        int open = 0;
        for (int y = 2; y <= 9; y += 3) {
            for (int x = -5; x <= 5; x += 5) {
                for (int z = -5; z <= 5; z += 5) {
                    if (level.isEmptyBlock(floor.offset(x, y, z))) {
                        open++;
                    }
                }
            }
        }
        return open >= 20;
    }

    private static void prepareGarden(WorldGenLevel level, BlockPos floor, RandomSource random) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (x * x + z * z > 30) {
                    continue;
                }
                cursor.set(floor.getX() + x, floor.getY(), floor.getZ() + z);
                if (level.getBlockState(cursor).isFaceSturdy(level, cursor, Direction.UP)
                    && level.isEmptyBlock(cursor.above())) {
                    level.setBlock(cursor, random.nextInt(5) == 0
                        ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : Blocks.ROOTED_DIRT.defaultBlockState(), 2);
                    if (random.nextInt(9) == 0) {
                        BlockPos plantPos = cursor.above();
                        BlockState plant = WorldgenBlocks.rareFlower(level, plantPos, random.nextInt(32));
                        if (plant.canSurvive(level, plantPos)) {
                            level.setBlock(plantPos, plant, 2);
                        }
                    }
                }
            }
        }
    }

    private static void growTree(WorldGenLevel level, BlockPos base, int height, RandomSource random) {
        for (int y = 0; y < height; y++) {
            BlockPos trunk = base.above(y);
            if (!level.isEmptyBlock(trunk) && !level.getBlockState(trunk).canBeReplaced()) {
                break;
            }
            level.setBlock(trunk, Blocks.DARK_OAK_LOG.defaultBlockState(), 2);
        }

        BlockPos crown = base.above(height - 1);
        for (int y = -2; y <= 3; y++) {
            int radius = y >= 2 ? 2 : 4;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + random.nextInt(3)
                        || random.nextInt(8) == 0) {
                        continue;
                    }
                    BlockPos leaf = crown.offset(x, y, z);
                    if (level.isEmptyBlock(leaf)) {
                        level.setBlock(leaf, Blocks.AZALEA_LEAVES.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void carveShallowOculus(WorldGenLevel level, BlockPos start) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, start.getX(), start.getZ());
        if (surfaceY - start.getY() < 5 || surfaceY - start.getY() > 18) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = start.getY(); y <= surfaceY; y++) {
            int radius = y > surfaceY - 4 ? 2 : 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z <= radius * radius) {
                        cursor.set(start.getX() + x, y, start.getZ() + z);
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
