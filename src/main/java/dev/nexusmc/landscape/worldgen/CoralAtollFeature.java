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
 * A low warm-ocean island: sand ring, sheltered lagoon and an outer coral shelf.
 */
public final class CoralAtollFeature extends Feature<NoneFeatureConfiguration> {
    private static final Block[] CORALS = {
        Blocks.TUBE_CORAL_BLOCK,
        Blocks.BRAIN_CORAL_BLOCK,
        Blocks.BUBBLE_CORAL_BLOCK,
        Blocks.FIRE_CORAL_BLOCK,
        Blocks.HORN_CORAL_BLOCK
    };

    public CoralAtollFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos placementOrigin = context.origin();
        int centerX = (placementOrigin.getX() & ~15) + 8;
        int centerZ = (placementOrigin.getZ() & ~15) + 8;
        BlockPos origin = new BlockPos(
            centerX,
            level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, centerX, centerZ),
            centerZ
        );
        int floorY = level.getHeight(
            Heightmap.Types.OCEAN_FLOOR_WG,
            origin.getX(),
            origin.getZ()
        );
        if (floorY < 46 || floorY > 58) {
            return false;
        }

        int radiusX = 15 + random.nextInt(5);
        int radiusZ = 13 + random.nextInt(5);
        int sandY = 64;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;

        for (int x = -radiusX - 3; x <= radiusX + 3; x++) {
            for (int z = -radiusZ - 3; z <= radiusZ + 3; z++) {
                double distance = (x * x) / (double) (radiusX * radiusX)
                    + (z * z) / (double) (radiusZ * radiusZ);
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;

                if (distance >= 0.45 && distance <= 1.08) {
                    int top = sandY + (random.nextInt(7) == 0 ? 2 : random.nextInt(2));
                    int naturalFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, worldX, worldZ);
                    if (naturalFloor < 45 || naturalFloor > 60) {
                        continue;
                    }
                    for (int y = naturalFloor; y <= top; y++) {
                        cursor.set(worldX, y, worldZ);
                        level.setBlock(cursor, y >= top - 3
                            ? Blocks.SAND.defaultBlockState()
                            : Blocks.SANDSTONE.defaultBlockState(), 2);
                    }
                    changed++;
                } else if (distance > 1.08 && distance < 1.5 && random.nextInt(3) == 0) {
                    int naturalFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, worldX, worldZ);
                    cursor.set(worldX, naturalFloor, worldZ);
                    BlockState coral = CORALS[random.nextInt(CORALS.length)].defaultBlockState();
                    level.setBlock(cursor, coral, 2);
                }
            }
        }
        if (changed > 90) {
            addPalm(level, origin.offset(radiusX / 2, sandY + 1 - origin.getY(), 0), random);
            dev.nexusmc.landscape.diagnostics.WorldgenSurvey.recordFeature("coral_atoll");
            return true;
        }
        return false;
    }

    private static void addPalm(WorldGenLevel level, BlockPos base, RandomSource random) {
        int height = 6 + random.nextInt(4);
        for (int y = 0; y < height; y++) {
            level.setBlock(base.above(y), Blocks.JUNGLE_LOG.defaultBlockState(), 2);
        }
        BlockPos crown = base.above(height);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) + Math.abs(z) <= 4) {
                    level.setBlock(
                        crown.offset(x, random.nextInt(2), z),
                        Blocks.JUNGLE_LEAVES.defaultBlockState(),
                        2
                    );
                }
            }
        }
    }
}
