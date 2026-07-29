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
        BlockPos placementOrigin = context.origin();
        BlockPos origin = new BlockPos(
            (placementOrigin.getX() & ~15) + 8,
            placementOrigin.getY(),
            (placementOrigin.getZ() & ~15) + 8
        );
        int oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
        if (oceanFloor > 58 || origin.getY() < 112 || !nearArchipelagoEdge(level, origin)) {
            return false;
        }

        int radiusX = 17 + random.nextInt(7);
        int radiusZ = 15 + random.nextInt(7);
        int depth = 19 + random.nextInt(11);
        double phaseX = random.nextDouble() * Math.PI * 2.0;
        double phaseZ = random.nextDouble() * Math.PI * 2.0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                double horizontal = (x * x) / (double) (radiusX * radiusX)
                    + (z * z) / (double) (radiusZ * radiusZ);
                horizontal += Math.sin(x * 0.31 + phaseX) * 0.055;
                horizontal += Math.cos(z * 0.27 + phaseZ) * 0.055;
                if (horizontal > 1.0) {
                    continue;
                }
                int topOffset = Mth.clamp(
                    (int) Math.round(
                        Math.sin(x * 0.18 + phaseZ) + Math.cos(z * 0.21 + phaseX)
                    ),
                    -1,
                    2
                );
                int localDepth = Mth.clamp(
                    (int) (Math.pow(1.0 - horizontal, 0.62) * depth)
                        + random.nextInt(4),
                    2,
                    depth
                );
                for (int y = 0; y <= localDepth; y++) {
                    double taper = y / (double) depth;
                    if (horizontal + taper * taper * 0.92 > 1.08) {
                        continue;
                    }
                    cursor.set(
                        origin.getX() + x,
                        origin.getY() + topOffset - y,
                        origin.getZ() + z
                    );
                    if (y == 0) {
                        level.setBlock(cursor, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    } else if (y < 4) {
                        level.setBlock(cursor, Blocks.DIRT.defaultBlockState(), 2);
                    } else {
                        level.setBlock(
                            cursor,
                            random.nextInt(9) == 0
                                ? Blocks.CALCITE.defaultBlockState()
                                : random.nextInt(7) == 0
                                    ? Blocks.ANDESITE.defaultBlockState()
                                    : Blocks.STONE.defaultBlockState(),
                            2
                        );
                    }
                }
            }
        }

        addLandmarkTree(level, origin.offset(-radiusX / 4, 2, radiusZ / 6), random);
        if (random.nextBoolean()) {
            addLandmarkTree(level, origin.offset(radiusX / 4, 1, -radiusZ / 5), random);
        }
        dev.nexusmc.landscape.diagnostics.WorldgenSurvey.recordFeature("floating_island");
        return true;
    }

    private static boolean nearArchipelagoEdge(WorldGenLevel level, BlockPos origin) {
        int raisedNeighbors = 0;
        int radius = 64;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                int floor = level.getHeight(
                    Heightmap.Types.OCEAN_FLOOR_WG,
                    origin.getX() + x * radius,
                    origin.getZ() + z * radius
                );
                if (floor >= 61 && floor <= 112) {
                    raisedNeighbors++;
                }
            }
        }
        return raisedNeighbors >= 2;
    }

    private static void addLandmarkTree(WorldGenLevel level, BlockPos base, RandomSource random) {
        int height = 9 + random.nextInt(7);
        for (int y = 0; y < height; y++) {
            level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        BlockPos crown = base.above(height);
        for (int x = -4; x <= 4; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -4; z <= 4; z++) {
                    if (x * x + z * z + y * y * 2 <= 17 + random.nextInt(6)) {
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
