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
        BlockPos origin = context.origin();
        int floorY = level.getHeight(
            Heightmap.Types.OCEAN_FLOOR_WG,
            origin.getX(),
            origin.getZ()
        );
        if (floorY > 57) {
            return false;
        }

        int radiusX = 11 + random.nextInt(6);
        int radiusZ = 9 + random.nextInt(5);
        int sandY = 62;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;

        for (int x = -radiusX - 3; x <= radiusX + 3; x++) {
            for (int z = -radiusZ - 3; z <= radiusZ + 3; z++) {
                double distance = (x * x) / (double) (radiusX * radiusX)
                    + (z * z) / (double) (radiusZ * radiusZ);
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;

                if (distance >= 0.45 && distance <= 1.08) {
                    int top = sandY + (random.nextInt(6) == 0 ? 2 : random.nextInt(2));
                    int naturalFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, worldX, worldZ);
                    for (int y = Math.max(naturalFloor, top - 4); y < top; y++) {
                        cursor.set(worldX, y, worldZ);
                        level.setBlock(cursor, y >= top - 2
                            ? Blocks.SAND.defaultBlockState()
                            : Blocks.SANDSTONE.defaultBlockState(), 2);
                    }
                    changed++;
                } else if (distance > 1.08 && distance < 1.42 && random.nextInt(4) == 0) {
                    int naturalFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, worldX, worldZ);
                    cursor.set(worldX, naturalFloor, worldZ);
                    BlockState coral = CORALS[random.nextInt(CORALS.length)].defaultBlockState();
                    level.setBlock(cursor, coral, 2);
                }
            }
        }
        return changed > 30;
    }
}
