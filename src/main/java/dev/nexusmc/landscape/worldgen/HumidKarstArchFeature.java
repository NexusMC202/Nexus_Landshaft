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

/**
 * A sparse humid-region karst cluster. Independent, asymmetric limestone
 * towers read as eroded terrain rather than a constructed gateway. A broken
 * high saddle is possible, but deliberately uncommon.
 */
public final class HumidKarstArchFeature extends Feature<NoneFeatureConfiguration> {
    public HumidKarstArchFeature(Codec<NoneFeatureConfiguration> codec) {
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
        double angle = random.nextDouble() * Math.PI;
        int halfSpan = 8 + random.nextInt(5);
        int axisX = (int) Math.round(Math.cos(angle) * halfSpan);
        int axisZ = (int) Math.round(Math.sin(angle) * halfSpan);
        BlockPos first = surfaceAt(level, origin.offset(-axisX, 0, -axisZ));
        BlockPos second = surfaceAt(level, origin.offset(axisX, 0, axisZ));
        if (!validGround(level, first) || !validGround(level, second)
            || Math.abs(first.getY() - second.getY()) > 9) {
            return false;
        }

        int firstHeight = 18 + random.nextInt(15);
        int secondHeight = 15 + random.nextInt(14);
        buildTower(level, first, firstHeight, random);
        buildTower(level, second, secondHeight, random);

        int satellites = 1 + random.nextInt(3);
        for (int i = 0; i < satellites; i++) {
            double satelliteAngle = angle + 0.8 + random.nextDouble() * 4.7;
            int distance = 7 + random.nextInt(10);
            BlockPos satellite = surfaceAt(
                level,
                origin.offset(
                    (int) Math.round(Math.cos(satelliteAngle) * distance),
                    0,
                    (int) Math.round(Math.sin(satelliteAngle) * distance)
                )
            );
            if (validGround(level, satellite)) {
                buildTower(level, satellite, 8 + random.nextInt(12), random);
            }
        }

        if (random.nextInt(4) == 0 && Math.abs(firstHeight - secondHeight) < 9) {
            int saddleY = Math.max(first.getY(), second.getY())
                + Math.min(firstHeight, secondHeight) - 5;
            buildBrokenSaddle(level, first, second, saddleY, random);
        }
        dev.nexusmc.landscape.diagnostics.WorldgenSurvey.recordFeature("humid_karst_cluster");
        return true;
    }

    private static BlockPos surfaceAt(WorldGenLevel level, BlockPos pos) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static boolean validGround(WorldGenLevel level, BlockPos surface) {
        return level.getFluidState(surface).isEmpty()
            && !level.getBlockState(surface.below()).isAir()
            && surface.getY() < level.getMaxBuildHeight() - 28;
    }

    private static void buildTower(
        WorldGenLevel level,
        BlockPos base,
        int height,
        RandomSource random
    ) {
        double phase = random.nextDouble() * Math.PI * 2.0;
        for (int y = -2; y <= height; y++) {
            double normalized = Math.max(0.0, y) / Math.max(1.0, height);
            double centerX = Math.sin(y * 0.21 + phase) * 1.1;
            double centerZ = Math.cos(y * 0.17 + phase) * 0.9;
            double radiusX = 4.8 - normalized * 2.1
                + Math.sin(y * 0.43 + phase) * 0.65;
            double radiusZ = 4.1 - normalized * 1.6
                + Math.cos(y * 0.37 + phase) * 0.55;
            if (normalized > 0.72) {
                radiusX += 0.8;
                radiusZ += 0.7;
            }
            int extent = 6;
            for (int x = -extent; x <= extent; x++) {
                for (int z = -extent; z <= extent; z++) {
                    double distance = ((x - centerX) * (x - centerX)) / (radiusX * radiusX)
                        + ((z - centerZ) * (z - centerZ)) / (radiusZ * radiusZ);
                    double erosion = Math.sin((x + z) * 1.7 + y * 0.31 + phase) * 0.12;
                    if (distance + erosion > 1.0) {
                        continue;
                    }
                    BlockPos pos = base.offset(x, y, z);
                    level.setBlock(pos, limestone(random), 2);
                }
            }
        }
        for (int i = 0; i < height * 2; i++) {
            BlockPos moss = base.offset(
                random.nextInt(11) - 5,
                random.nextInt(Math.max(2, height)),
                random.nextInt(11) - 5
            );
            if (level.isEmptyBlock(moss) && hasSolidNeighbor(level, moss)) {
                level.setBlock(
                    moss,
                    random.nextInt(3) == 0
                        ? Blocks.VINE.defaultBlockState()
                        : Blocks.MOSS_CARPET.defaultBlockState(),
                    2
                );
            }
        }
    }

    private static void buildBrokenSaddle(
        WorldGenLevel level,
        BlockPos first,
        BlockPos second,
        int y,
        RandomSource random
    ) {
        int steps = Math.max(
            Math.abs(second.getX() - first.getX()),
            Math.abs(second.getZ() - first.getZ())
        );
        int gapCenter = steps / 2 + random.nextInt(5) - 2;
        for (int step = 2; step < steps - 1; step++) {
            if (Math.abs(step - gapCenter) <= 1) {
                continue;
            }
            double progress = step / (double) steps;
            int centerX = (int) Math.round(first.getX() + (second.getX() - first.getX()) * progress);
            int centerZ = (int) Math.round(first.getZ() + (second.getZ() - first.getZ()) * progress);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 1 || random.nextInt(4) == 0) {
                        level.setBlock(
                            new BlockPos(centerX + dx, y + random.nextInt(3) - 1, centerZ + dz),
                            limestone(random),
                            2
                        );
                    }
                }
            }
        }
    }

    private static boolean hasSolidNeighbor(WorldGenLevel level, BlockPos pos) {
        return !level.getBlockState(pos.north()).isAir()
            || !level.getBlockState(pos.south()).isAir()
            || !level.getBlockState(pos.east()).isAir()
            || !level.getBlockState(pos.west()).isAir()
            || !level.getBlockState(pos.below()).isAir();
    }

    private static BlockState limestone(RandomSource random) {
        return random.nextInt(7) == 0
            ? Blocks.CALCITE.defaultBlockState()
            : random.nextInt(5) == 0
                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
    }
}
