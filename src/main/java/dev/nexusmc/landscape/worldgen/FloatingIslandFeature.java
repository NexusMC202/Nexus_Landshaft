package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class FloatingIslandFeature extends Feature<NoneFeatureConfiguration> {
    public FloatingIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
        if (oceanFloor > 58 || origin.getY() < 112) {
            return false;
        }

        int radiusX = 10 + random.nextInt(8);
        int radiusZ = 10 + random.nextInt(8);
        int depth = 8 + random.nextInt(9);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                double horizontal = (x * x) / (double) (radiusX * radiusX)
                    + (z * z) / (double) (radiusZ * radiusZ);
                if (horizontal > 1.0) {
                    continue;
                }
                int localDepth = Mth.clamp((int) ((1.0 - horizontal) * depth) + random.nextInt(3), 1, depth);
                for (int y = 0; y <= localDepth; y++) {
                    double taper = y / (double) depth;
                    if (horizontal + taper * taper > 1.05) {
                        continue;
                    }
                    cursor.set(origin.getX() + x, origin.getY() - y, origin.getZ() + z);
                    if (y == 0) {
                        level.setBlock(cursor, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    } else if (y < 3) {
                        level.setBlock(cursor, Blocks.DIRT.defaultBlockState(), 2);
                    } else {
                        level.setBlock(cursor, random.nextInt(7) == 0
                            ? Blocks.CALCITE.defaultBlockState()
                            : Blocks.STONE.defaultBlockState(), 2);
                    }
                }
            }
        }

        addLandmarkTree(level, origin.above(), random);
        return true;
    }

    private static void addLandmarkTree(WorldGenLevel level, BlockPos base, RandomSource random) {
        int height = 6 + random.nextInt(5);
        for (int y = 0; y < height; y++) {
            level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        BlockPos crown = base.above(height);
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (x * x + z * z + y * y * 2 <= 10 + random.nextInt(4)) {
                        BlockPos pos = crown.offset(x, y, z);
                        if (level.isEmptyBlock(pos)) {
                            level.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }
}
