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
 * A large traversable stone arch built from two irregular cliff towers. The
 * opening is deliberately broad enough for Aeronautics ground vehicles.
 */
public final class MountainArchFeature extends Feature<NoneFeatureConfiguration> {
    public MountainArchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean alongX = random.nextBoolean();
        int halfSpan = 10 + random.nextInt(5);

        BlockPos first = surface(level, origin, alongX ? -halfSpan : 0, alongX ? 0 : -halfSpan);
        BlockPos second = surface(level, origin, alongX ? halfSpan : 0, alongX ? 0 : halfSpan);
        if (!validBase(level, first) || !validBase(level, second)
            || Math.abs(first.getY() - second.getY()) > 10) {
            return false;
        }

        int springY = Math.max(first.getY(), second.getY()) + 17 + random.nextInt(8);
        if (springY + 10 >= level.getMaxBuildHeight()) {
            return false;
        }

        buildTower(level, first, springY - first.getY() + 5, random);
        buildTower(level, second, springY - second.getY() + 5, random);
        buildBridge(level, origin, alongX, halfSpan, springY, random);
        weather(level, first, second, random);
        dev.nexusmc.landscape.diagnostics.WorldgenSurvey.recordFeature("mountain_arch");
        return true;
    }

    private static BlockPos surface(
        WorldGenLevel level,
        BlockPos origin,
        int offsetX,
        int offsetZ
    ) {
        int x = origin.getX() + offsetX;
        int z = origin.getZ() + offsetZ;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        return new BlockPos(x, y, z);
    }

    private static boolean validBase(WorldGenLevel level, BlockPos base) {
        return level.getFluidState(base).isEmpty()
            && !level.getBlockState(base.below()).isAir();
    }

    private static void buildTower(
        WorldGenLevel level,
        BlockPos base,
        int height,
        RandomSource random
    ) {
        int leanDirectionX = random.nextBoolean() ? 1 : -1;
        int leanDirectionZ = random.nextBoolean() ? 1 : -1;
        for (int y = -3; y <= height; y++) {
            double progress = Math.max(0, y) / (double) height;
            int radius = progress < 0.25 ? 5 : progress < 0.72 ? 4 : 3;
            int leanX = y / 12 * leanDirectionX;
            int leanZ = y / 15 * leanDirectionZ;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double roughRadius = radius * radius + random.nextInt(5) - 2;
                    if (x * x + z * z > roughRadius) {
                        continue;
                    }
                    level.setBlock(
                        base.offset(x + leanX, y, z + leanZ),
                        rock(random, y > height - 2),
                        2
                    );
                }
            }
        }
    }

    private static void buildBridge(
        WorldGenLevel level,
        BlockPos origin,
        boolean alongX,
        int halfSpan,
        int springY,
        RandomSource random
    ) {
        for (int along = -halfSpan; along <= halfSpan; along++) {
            double normalized = along / (double) halfSpan;
            int rise = (int) Math.round((1.0 - normalized * normalized) * 9.0);
            int centerX = origin.getX() + (alongX ? along : 0);
            int centerZ = origin.getZ() + (alongX ? 0 : along);
            int halfWidth = Math.abs(along) > halfSpan - 3 ? 4 : 3;
            for (int width = -halfWidth; width <= halfWidth; width++) {
                for (int thickness = -2; thickness <= 2; thickness++) {
                    if (Math.abs(width) == halfWidth && Math.abs(thickness) == 2
                        && random.nextBoolean()) {
                        continue;
                    }
                    BlockPos position = new BlockPos(
                        centerX + (alongX ? 0 : width),
                        springY + rise + thickness,
                        centerZ + (alongX ? width : 0)
                    );
                    level.setBlock(position, rock(random, thickness == 2), 2);
                }
            }
        }
    }

    private static void weather(
        WorldGenLevel level,
        BlockPos first,
        BlockPos second,
        RandomSource random
    ) {
        for (int i = 0; i < 42; i++) {
            BlockPos base = random.nextBoolean() ? first : second;
            BlockPos position = base.offset(
                random.nextInt(11) - 5,
                random.nextInt(20),
                random.nextInt(11) - 5
            );
            if (level.isEmptyBlock(position) && !level.isEmptyBlock(position.below())) {
                level.setBlock(position, Blocks.MOSS_CARPET.defaultBlockState(), 2);
            }
        }
    }

    private static BlockState rock(RandomSource random, boolean exposed) {
        if (exposed && random.nextInt(7) == 0) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (random.nextInt(8) == 0) {
            return Blocks.CALCITE.defaultBlockState();
        }
        return random.nextInt(5) == 0
            ? Blocks.ANDESITE.defaultBlockState()
            : Blocks.STONE.defaultBlockState();
    }
}
