package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Turns a large Deep Dark chamber into an asymmetric sculk rift with rib-like
 * deepslate formations, summon-capable shriekers and sparse soul fire.
 */
public final class DeepDarkRiftFeature extends Feature<NoneFeatureConfiguration> {
    public DeepDarkRiftFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos floor = CaveFeatureSupport.findFloor(level, context.origin(), 26);
        if (floor == null || !CaveFeatureSupport.hasRoom(level, floor, 7, 11)) {
            return false;
        }

        corruptFloor(level, floor, random);
        buildRibs(level, floor, random);
        seedThreats(level, floor, random);
        return true;
    }

    private static void corruptFloor(WorldGenLevel level, BlockPos floor, RandomSource random) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if (x * x + z * z > 48 + random.nextInt(18)) {
                    continue;
                }
                BlockPos pos = floor.offset(x, 0, z);
                if (!level.getBlockState(pos).isAir() && level.isEmptyBlock(pos.above())) {
                    level.setBlock(pos, random.nextInt(6) == 0
                        ? Blocks.SCULK_CATALYST.defaultBlockState()
                        : Blocks.SCULK.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void buildRibs(WorldGenLevel level, BlockPos floor, RandomSource random) {
        Direction[] directions = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };
        for (Direction direction : directions) {
            int distance = 4 + random.nextInt(4);
            BlockPos base = floor.relative(direction, distance);
            int height = 6 + random.nextInt(7);
            for (int y = 0; y < height; y++) {
                int inward = y > height / 2 ? (y - height / 2) / 2 : 0;
                BlockPos pos = base.above(y).relative(direction.getOpposite(), inward);
                level.setBlock(pos, random.nextInt(5) == 0
                    ? Blocks.REINFORCED_DEEPSLATE.defaultBlockState()
                    : Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
            }
        }
    }

    private static void seedThreats(WorldGenLevel level, BlockPos floor, RandomSource random) {
        BlockPos shrieker = floor.offset(2, 1, -1);
        if (level.isEmptyBlock(shrieker)) {
            level.setBlock(shrieker, Blocks.SCULK_SHRIEKER.defaultBlockState()
                .setValue(SculkShriekerBlock.CAN_SUMMON, true), 2);
        }
        BlockPos sensor = floor.offset(-3, 1, 2);
        if (level.isEmptyBlock(sensor)) {
            level.setBlock(sensor, Blocks.SCULK_SENSOR.defaultBlockState(), 2);
        }
        for (int i = 0; i < 3; i++) {
            BlockPos fireBase = floor.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
            if (level.isEmptyBlock(fireBase.above())) {
                level.setBlock(fireBase, Blocks.SOUL_SOIL.defaultBlockState(), 2);
                level.setBlock(fireBase.above(), Blocks.SOUL_FIRE.defaultBlockState(), 2);
            }
        }
    }
}
