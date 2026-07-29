package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class HotSpringFeature extends Feature<NoneFeatureConfiguration> {
    public HotSpringFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        if (origin.getY() < level.getMinBuildHeight() + 8
            || origin.getY() > level.getMaxBuildHeight() - 8) {
            return false;
        }

        int radiusX = 5 + random.nextInt(4);
        int radiusZ = 5 + random.nextInt(4);
        int waterY = origin.getY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;

        for (int x = -radiusX - 1; x <= radiusX + 1; x++) {
            for (int z = -radiusZ - 1; z <= radiusZ + 1; z++) {
                double distance = (x * x) / (double) (radiusX * radiusX)
                    + (z * z) / (double) (radiusZ * radiusZ);
                if (distance > 1.28) {
                    continue;
                }

                cursor.set(origin.getX() + x, waterY, origin.getZ() + z);
                int depth = distance < 0.35 ? 3 : distance < 0.78 ? 2 : 1;
                BlockState floor = random.nextInt(5) == 0
                    ? Blocks.CALCITE.defaultBlockState()
                    : Blocks.TUFF.defaultBlockState();

                if (distance <= 1.0) {
                    for (int dy = 0; dy < depth; dy++) {
                        level.setBlock(cursor.below(dy), Blocks.WATER.defaultBlockState(), 2);
                    }
                    level.setBlock(cursor.below(depth), floor, 2);
                    if (distance < 0.28 && random.nextInt(7) == 0) {
                        level.setBlock(cursor.below(depth + 1), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                    }
                    changed++;
                } else {
                    level.setBlock(cursor.below(), floor, 2);
                    if (random.nextInt(7) == 0 && level.isEmptyBlock(cursor)) {
                        BlockState flower = WorldgenBlocks.rareFlower(level, cursor, random.nextInt(16));
                        if (flower.canSurvive(level, cursor)) {
                            level.setBlock(cursor, flower, 2);
                        }
                    }
                }
            }
        }

        return changed > 20;
    }
}
