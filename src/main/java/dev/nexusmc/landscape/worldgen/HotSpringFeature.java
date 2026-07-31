package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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

        int radiusX = 7 + random.nextInt(5);
        int radiusZ = 6 + random.nextInt(5);
        int waterY = origin.getY() - 1;
        if (!hasStableFoundation(level, origin, radiusX, radiusZ)) {
            return false;
        }

        double shoulderX = radiusX * (random.nextBoolean() ? 0.32 : -0.32);
        double shoulderZ = radiusZ * (random.nextBoolean() ? 0.24 : -0.24);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;

        for (int x = -radiusX - 2; x <= radiusX + 2; x++) {
            for (int z = -radiusZ - 2; z <= radiusZ + 2; z++) {
                double mainDistance = (x * x) / (double) (radiusX * radiusX)
                    + (z * z) / (double) (radiusZ * radiusZ);
                double shoulderDistance = ((x - shoulderX) * (x - shoulderX))
                    / ((radiusX * 0.72) * (radiusX * 0.72))
                    + ((z - shoulderZ) * (z - shoulderZ))
                    / ((radiusZ * 0.68) * (radiusZ * 0.68));
                double distance = Math.min(mainDistance, shoulderDistance + 0.08);
                if (distance > 1.28) {
                    continue;
                }

                cursor.set(origin.getX() + x, waterY, origin.getZ() + z);
                int depth = distance < 0.28 ? 4 : distance < 0.72 ? 3 : 2;
                BlockState floor = springStone(random);

                if (distance <= 1.0) {
                    for (int dy = 0; dy < depth; dy++) {
                        level.setBlock(cursor.below(dy), Blocks.WATER.defaultBlockState(), 2);
                    }
                    level.setBlock(cursor.below(depth), floor, 2);
                    if (distance < 0.24 && random.nextInt(10) == 0) {
                        level.setBlock(cursor.below(depth + 1), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                    }
                    changed++;
                } else {
                    if (random.nextInt(5) != 0) {
                        level.setBlock(cursor, floor, 2);
                    }
                    BlockPos decoration = cursor.above();
                    if (random.nextInt(10) == 0 && level.isEmptyBlock(decoration)) {
                        BlockState flower = WorldgenBlocks.rareFlower(
                            level,
                            decoration,
                            random.nextInt(16)
                        );
                        if (flower.canSurvive(level, decoration)) {
                            level.setBlock(decoration, flower, 2);
                        }
                    }
                }
            }
        }

        if (changed > 20) {
            dev.nexusmc.landscape.diagnostics.WorldgenSurvey.recordFeature("hot_spring");
            return true;
        }
        return false;
    }

    private static boolean hasStableFoundation(
        WorldGenLevel level,
        BlockPos origin,
        int radiusX,
        int radiusZ
    ) {
        int expectedY = origin.getY();
        int[][] samples = {
            {0, 0},
            {radiusX, 0},
            {-radiusX, 0},
            {0, radiusZ},
            {0, -radiusZ}
        };
        for (int[] sample : samples) {
            int x = origin.getX() + sample[0];
            int z = origin.getZ() + sample[1];
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            if (Math.abs(surfaceY - expectedY) > 3) {
                return false;
            }
        }
        for (int depth = 1; depth <= 6; depth++) {
            if (level.getBlockState(origin.below(depth)).isAir()
                || !level.getFluidState(origin.below(depth)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static BlockState springStone(RandomSource random) {
        if (random.nextInt(7) == 0) {
            return Blocks.CALCITE.defaultBlockState();
        }
        if (random.nextInt(6) == 0) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        return Blocks.TUFF.defaultBlockState();
    }
}
