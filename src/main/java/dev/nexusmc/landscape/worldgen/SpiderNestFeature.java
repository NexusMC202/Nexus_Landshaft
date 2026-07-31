package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A web territory with a cocoon-like nest core. Mob spawning is deliberately
 * kept out of chunk decoration: configuring a live spawner here can request
 * neighbouring chunks while they are still generating and deadlock the
 * integrated server.
 */
public final class SpiderNestFeature extends Feature<NoneFeatureConfiguration> {
    public SpiderNestFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos floor = CaveFeatureSupport.findFloor(level, context.origin(), 30);
        if (floor == null || !CaveFeatureSupport.hasRoom(level, floor, 7, 10)) {
            return false;
        }

        weaveWebs(level, floor, random);
        buildNestCore(level, floor, random);
        return true;
    }

    private static void weaveWebs(WorldGenLevel level, BlockPos floor, RandomSource random) {
        for (int i = 0; i < 95; i++) {
            BlockPos pos = floor.offset(
                random.nextInt(17) - 8,
                1 + random.nextInt(9),
                random.nextInt(17) - 8
            );
            if (!level.isEmptyBlock(pos)) {
                continue;
            }
            boolean anchored = false;
            for (Direction direction : Direction.values()) {
                if (!level.isEmptyBlock(pos.relative(direction))) {
                    anchored = true;
                    break;
                }
            }
            if (anchored || random.nextInt(5) == 0) {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 2);
                if (random.nextInt(4) == 0 && level.isEmptyBlock(pos.below())) {
                    level.setBlock(pos.below(), Blocks.COBWEB.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void buildNestCore(WorldGenLevel level, BlockPos floor, RandomSource random) {
        level.setBlock(floor, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
        for (int y = 1; y <= 3; y++) {
            int radius = y == 2 ? 2 : 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + 1) {
                        continue;
                    }
                    BlockPos cocoon = floor.offset(x, y, z);
                    if (level.isEmptyBlock(cocoon)) {
                        level.setBlock(cocoon, Blocks.COBWEB.defaultBlockState(), 2);
                    }
                }
            }
        }

        for (int i = 0; i < 14; i++) {
            BlockPos bone = floor.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
            if (level.isEmptyBlock(bone.above())) {
                level.setBlock(bone, random.nextInt(3) == 0
                    ? Blocks.BONE_BLOCK.defaultBlockState()
                    : Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
            }
        }
    }
}
