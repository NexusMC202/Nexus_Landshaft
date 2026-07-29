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
 * A sparse landmark for humid climate regions: twin limestone towers joined
 * by a natural arch. Its low placement rate keeps vehicle routes open.
 */
public final class HumidKarstArchFeature extends Feature<NoneFeatureConfiguration> {
    public HumidKarstArchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean alongX = random.nextBoolean();
        int halfSpan = 6 + random.nextInt(4);

        BlockPos first = surfaceAt(level, alongX
            ? origin.offset(-halfSpan, 0, 0)
            : origin.offset(0, 0, -halfSpan));
        BlockPos second = surfaceAt(level, alongX
            ? origin.offset(halfSpan, 0, 0)
            : origin.offset(0, 0, halfSpan));
        if (!validGround(level, first) || !validGround(level, second)
            || Math.abs(first.getY() - second.getY()) > 7) {
            return false;
        }

        int archBaseY = Math.max(first.getY(), second.getY()) + 11 + random.nextInt(5);
        buildPillar(level, first, archBaseY - first.getY() + 3, random);
        buildPillar(level, second, archBaseY - second.getY() + 3, random);
        buildArch(level, origin, alongX, halfSpan, archBaseY, random);
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

    private static void buildPillar(
        WorldGenLevel level,
        BlockPos base,
        int height,
        RandomSource random
    ) {
        for (int y = -2; y <= height; y++) {
            double taper = Math.max(0.0, y) / Math.max(1.0, height);
            int radius = taper > 0.75 ? 2 : 3;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + random.nextInt(3)) {
                        continue;
                    }
                    BlockPos pos = base.offset(x, y, z);
                    level.setBlock(pos, limestone(random), 2);
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            BlockPos moss = base.offset(
                random.nextInt(7) - 3,
                random.nextInt(Math.max(2, height)),
                random.nextInt(7) - 3
            );
            if (level.isEmptyBlock(moss) && !level.isEmptyBlock(moss.below())) {
                level.setBlock(moss, Blocks.MOSS_CARPET.defaultBlockState(), 2);
            }
        }
    }

    private static void buildArch(
        WorldGenLevel level,
        BlockPos origin,
        boolean alongX,
        int halfSpan,
        int baseY,
        RandomSource random
    ) {
        for (int along = -halfSpan; along <= halfSpan; along++) {
            double normalized = along / (double) halfSpan;
            int rise = (int) Math.round((1.0 - normalized * normalized) * 4.0);
            int centerX = origin.getX() + (alongX ? along : 0);
            int centerZ = origin.getZ() + (alongX ? 0 : along);
            for (int thickness = -1; thickness <= 1; thickness++) {
                for (int width = -1; width <= 1; width++) {
                    BlockPos pos = new BlockPos(
                        centerX + (alongX ? 0 : width),
                        baseY + rise + thickness,
                        centerZ + (alongX ? width : 0)
                    );
                    level.setBlock(pos, limestone(random), 2);
                }
            }
        }
    }

    private static BlockState limestone(RandomSource random) {
        return random.nextInt(7) == 0
            ? Blocks.CALCITE.defaultBlockState()
            : random.nextInt(5) == 0
                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
    }
}
