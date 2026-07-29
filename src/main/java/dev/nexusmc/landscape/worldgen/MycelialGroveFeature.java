package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A clustered mushroom landmark with varied silhouettes, luminous undersides
 * and open space between stems.
 */
public final class MycelialGroveFeature extends Feature<NoneFeatureConfiguration> {
    public MycelialGroveFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!level.getFluidState(origin).isEmpty()) {
            return false;
        }

        paintMycelium(level, origin, random);
        int mushrooms = 3 + random.nextInt(4);
        int built = 0;
        for (int i = 0; i < mushrooms; i++) {
            int x = origin.getX() + random.nextInt(21) - 10;
            int z = origin.getZ() + random.nextInt(21) - 10;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos base = new BlockPos(x, y, z);
            if (level.getFluidState(base).isEmpty()
                && !level.getBlockState(base.below()).isAir()) {
                buildMushroom(level, base, 7 + random.nextInt(7), random.nextBoolean(), random);
                built++;
            }
        }
        return built >= 2;
    }

    private static void paintMycelium(WorldGenLevel level, BlockPos origin, RandomSource random) {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                if (x * x + z * z > 144 + random.nextInt(24)) {
                    continue;
                }
                int y = level.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    origin.getX() + x,
                    origin.getZ() + z
                );
                BlockPos ground = new BlockPos(origin.getX() + x, y - 1, origin.getZ() + z);
                if (!level.getBlockState(ground).isAir() && level.getFluidState(ground.above()).isEmpty()) {
                    level.setBlock(ground, random.nextInt(8) == 0
                        ? Blocks.PODZOL.defaultBlockState()
                        : Blocks.MYCELIUM.defaultBlockState(), 2);
                    if (random.nextInt(20) == 0) {
                        BlockPos plant = ground.above();
                        BlockState flower = WorldgenBlocks.rareFlower(level, plant, random.nextInt(64));
                        if (flower.canSurvive(level, plant)) {
                            level.setBlock(plant, flower, 2);
                        }
                    }
                }
            }
        }
    }

    private static void buildMushroom(
        WorldGenLevel level,
        BlockPos base,
        int height,
        boolean red,
        RandomSource random
    ) {
        for (int y = 0; y < height; y++) {
            level.setBlock(base.above(y), Blocks.MUSHROOM_STEM.defaultBlockState(), 2);
        }

        Block capBlock = red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK;
        BlockPos crown = base.above(height);
        if (red) {
            for (int y = -1; y <= 2; y++) {
                int radius = y == 2 ? 2 : y == 1 ? 4 : 3;
                placeCapLayer(level, crown.above(y), radius, capBlock, random);
            }
        } else {
            placeCapLayer(level, crown, 5, capBlock, random);
            placeCapLayer(level, crown.above(), 3, capBlock, random);
        }

        for (int i = 0; i < 3; i++) {
            BlockPos light = crown.offset(random.nextInt(5) - 2, -1, random.nextInt(5) - 2);
            if (level.isEmptyBlock(light)) {
                level.setBlock(light, Blocks.SHROOMLIGHT.defaultBlockState(), 2);
            }
        }
    }

    private static void placeCapLayer(
        WorldGenLevel level,
        BlockPos center,
        int radius,
        Block cap,
        RandomSource random
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius + random.nextInt(4)) {
                    continue;
                }
                BlockPos pos = center.offset(x, 0, z);
                if (level.getBlockState(pos).canBeReplaced()) {
                    level.setBlock(pos, cap.defaultBlockState(), 2);
                }
            }
        }
    }
}
