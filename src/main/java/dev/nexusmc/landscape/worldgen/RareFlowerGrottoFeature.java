package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A deliberately rare, quiet cave pocket with a spring and collectible flowers.
 */
public final class RareFlowerGrottoFeature extends Feature<NoneFeatureConfiguration> {
    public RareFlowerGrottoFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos floor = CaveFeatureSupport.findFloor(level, context.origin(), 24);
        if (floor == null || !CaveFeatureSupport.hasRoom(level, floor, 5, 8)) {
            return false;
        }

        makeMossShelf(level, floor, random);
        makeSpring(level, floor);
        plantFlowers(level, floor, random);
        hangSporeBlossoms(level, floor, random);
        return true;
    }

    private static void makeMossShelf(WorldGenLevel level, BlockPos floor, RandomSource random) {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (x * x + z * z > 34 + random.nextInt(8)) {
                    continue;
                }
                BlockPos pos = floor.offset(x, 0, z);
                if (level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)
                    && level.isEmptyBlock(pos.above())) {
                    level.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void makeSpring(WorldGenLevel level, BlockPos floor) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z <= 4) {
                    BlockPos water = floor.offset(x, 0, z);
                    level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
                    level.setBlock(water.below(), Blocks.CLAY.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void plantFlowers(WorldGenLevel level, BlockPos floor, RandomSource random) {
        for (int i = 0; i < 22; i++) {
            BlockPos plant = floor.offset(random.nextInt(13) - 6, 1, random.nextInt(13) - 6);
            if (!level.isEmptyBlock(plant)
                || !level.getBlockState(plant.below()).is(Blocks.MOSS_BLOCK)) {
                continue;
            }
            BlockState flower = WorldgenBlocks.rareFlower(level, plant, i + random.nextInt(32));
            if (flower.canSurvive(level, plant)) {
                level.setBlock(plant, flower, 2);
            }
        }
    }

    private static void hangSporeBlossoms(WorldGenLevel level, BlockPos floor, RandomSource random) {
        for (int i = 0; i < 8; i++) {
            BlockPos column = floor.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
            BlockPos ceiling = CaveFeatureSupport.ceilingAbove(level, column, 13);
            if (ceiling != null && level.isEmptyBlock(ceiling.below())) {
                BlockPos blossom = ceiling.below();
                BlockState state = Blocks.SPORE_BLOSSOM.defaultBlockState();
                if (state.canSurvive(level, blossom)) {
                    level.setBlock(blossom, state, 2);
                }
            }
        }
    }
}
