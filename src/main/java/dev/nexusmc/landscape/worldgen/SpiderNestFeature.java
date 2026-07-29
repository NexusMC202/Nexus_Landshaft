package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A web territory with a live spawner. If Nexus Mobs registers
 * nexus_mobs:giant_spider, the same generated nest automatically uses it.
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
        BlockPos spawnerPos = floor.above();
        level.setBlock(floor, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
        level.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 2);
        BlockEntity blockEntity = level.getBlockEntity(spawnerPos);
        if (blockEntity instanceof SpawnerBlockEntity spawner) {
            spawner.getSpawner().setEntityId(resolveSpider(), level.getLevel(), random, spawnerPos);
            spawner.setChanged();
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

    private static EntityType<?> resolveSpider() {
        return BuiltInRegistries.ENTITY_TYPE
            .getOptional(ResourceLocation.fromNamespaceAndPath("nexus_mobs", "giant_spider"))
            .orElse(EntityType.CAVE_SPIDER);
    }
}
