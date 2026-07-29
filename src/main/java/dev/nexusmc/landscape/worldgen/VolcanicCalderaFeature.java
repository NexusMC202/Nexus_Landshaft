package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A compact shield volcano with a broken basalt rim, lava bowl and cooled flow.
 * Large volcanic mountain chains come from terrain density; this feature gives
 * a rare province its recognizable summit.
 */
public final class VolcanicCalderaFeature extends Feature<NoneFeatureConfiguration> {
    public VolcanicCalderaFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos placementOrigin = context.origin();
        int centerX = (placementOrigin.getX() & ~15) + 8;
        int centerZ = (placementOrigin.getZ() & ~15) + 8;
        int centerY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
        BlockPos origin = new BlockPos(centerX, centerY, centerZ);
        if (!level.getFluidState(origin).isEmpty()
            || level.getBlockState(origin.below()).isAir()
            || origin.getY() <= level.getMinBuildHeight() + 4
            || origin.getY() > level.getMaxBuildHeight() - 32) {
            return false;
        }

        // Centering the feature in its placement chunk and keeping it below
        // 13 blocks guarantees it touches at most the immediately adjacent
        // chunks, which is safe during the FEATURES generation step.
        int radius = 10 + random.nextInt(3);
        int height = 11 + random.nextInt(5);
        int baseY = origin.getY() - 1;
        buildCone(level, origin, baseY, radius, height, random);
        carveCrater(level, origin, baseY, radius, height, random);
        addCooledFlow(level, origin, baseY, radius, random);
        dev.nexusmc.landscape.diagnostics.WorldgenSurvey.recordFeature("volcanic_caldera");
        return true;
    }

    private static void buildCone(
        WorldGenLevel level,
        BlockPos origin,
        int baseY,
        int radius,
        int height,
        RandomSource random
    ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z) / radius;
                if (distance > 1.0) {
                    continue;
                }
                int naturalY = level.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    origin.getX() + x,
                    origin.getZ() + z
                ) - 1;
                double rim = distance > 0.2 && distance < 0.42 ? 3.0 : 0.0;
                int topY = Math.max(naturalY,
                    baseY + Mth.floor((1.0 - distance) * height + rim + random.nextInt(2)));
                for (int y = naturalY; y <= topY; y++) {
                    cursor.set(origin.getX() + x, y, origin.getZ() + z);
                    level.setBlock(cursor, volcanicStone(random, y == topY), 2);
                }
            }
        }
    }

    private static void carveCrater(
        WorldGenLevel level,
        BlockPos origin,
        int baseY,
        int radius,
        int height,
        RandomSource random
    ) {
        int craterRadius = Math.max(4, radius / 4);
        int lavaY = baseY + height - 7;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -craterRadius; x <= craterRadius; x++) {
            for (int z = -craterRadius; z <= craterRadius; z++) {
                double distance = Math.sqrt(x * x + z * z) / craterRadius;
                if (distance > 1.0) {
                    continue;
                }
                int floorY = lavaY - (distance < 0.55 ? 1 : 0);
                for (int y = floorY + 1; y <= baseY + height + 5; y++) {
                    cursor.set(origin.getX() + x, y, origin.getZ() + z);
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }
                cursor.set(origin.getX() + x, floorY, origin.getZ() + z);
                level.setBlock(cursor, random.nextInt(4) == 0
                    ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : Blocks.BLACKSTONE.defaultBlockState(), 2);
                if (distance < 0.68) {
                    level.setBlock(cursor.above(), Blocks.LAVA.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void addCooledFlow(
        WorldGenLevel level,
        BlockPos origin,
        int baseY,
        int radius,
        RandomSource random
    ) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        for (int step = 3; step <= radius + 3; step++) {
            int x = Mth.floor(Math.cos(angle) * step);
            int z = Mth.floor(Math.sin(angle) * step);
            int y = level.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG,
                origin.getX() + x,
                origin.getZ() + z
            );
            BlockPos pos = new BlockPos(origin.getX() + x, Math.max(baseY, y - 1), origin.getZ() + z);
            level.setBlock(pos, step % 5 == 0
                ? Blocks.MAGMA_BLOCK.defaultBlockState()
                : Blocks.BASALT.defaultBlockState(), 2);
            if (random.nextBoolean()) {
                level.setBlock(pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1),
                    Blocks.SMOOTH_BASALT.defaultBlockState(), 2);
            }
        }
    }

    private static BlockState volcanicStone(RandomSource random, boolean surface) {
        if (surface && random.nextInt(6) == 0) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        return random.nextInt(5) == 0
            ? Blocks.BLACKSTONE.defaultBlockState()
            : random.nextInt(4) == 0
                ? Blocks.SMOOTH_BASALT.defaultBlockState()
                : Blocks.BASALT.defaultBlockState();
    }
}
