package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Small biome-aware landmarks that break up uniform vanilla surfaces while
 * preserving broad driveable corridors.
 */
public final class RegionalLandmarkFeature extends Feature<NoneFeatureConfiguration> {
    public RegionalLandmarkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x = context.origin().getX();
        int z = context.origin().getZ();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos surface = new BlockPos(x, y, z);
        if (!level.getFluidState(surface).isEmpty()
            || level.getBlockState(surface.below()).isAir()) {
            return false;
        }

        Holder<Biome> biome = level.getBiome(surface);
        boolean placed;
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.is(Biomes.DESERT)) {
            placed = buildHoodoo(level, surface, random);
        } else if (biome.is(BiomeTags.IS_MOUNTAIN)) {
            placed = buildBoulder(level, surface, random, Blocks.ANDESITE);
        } else if (biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA)
            || biome.is(Biomes.ICE_SPIKES)) {
            placed = buildBoulder(level, surface, random, Blocks.PACKED_ICE);
        } else if (biome.is(BiomeTags.IS_BEACH)) {
            placed = buildFallenLog(level, surface, random, Blocks.STRIPPED_OAK_LOG);
        } else if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) {
            placed = buildFallenLog(level, surface, random, Blocks.STRIPPED_SPRUCE_LOG);
        } else if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            placed = buildBoulder(level, surface, random, Blocks.MOSSY_COBBLESTONE);
        } else {
            placed = buildMeadowOutcrop(level, surface, random);
        }
        if (placed) {
            dev.nexusmc.landscape.diagnostics.WorldgenSurvey.recordFeature("regional_landmark");
        }
        return placed;
    }

    private static boolean buildHoodoo(
        WorldGenLevel level,
        BlockPos base,
        RandomSource random
    ) {
        int height = 6 + random.nextInt(8);
        for (int y = 0; y < height; y++) {
            int radius = y < 2 || y > height - 3 ? 2 : 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius + 1) {
                        continue;
                    }
                    BlockState state = y % 4 == 0
                        ? Blocks.YELLOW_TERRACOTTA.defaultBlockState()
                        : y % 3 == 0
                            ? Blocks.RED_TERRACOTTA.defaultBlockState()
                            : Blocks.TERRACOTTA.defaultBlockState();
                    level.setBlock(base.offset(x, y, z), state, 2);
                }
            }
        }
        return true;
    }

    private static boolean buildBoulder(
        WorldGenLevel level,
        BlockPos base,
        RandomSource random,
        Block block
    ) {
        int radiusX = 2 + random.nextInt(3);
        int radiusZ = 2 + random.nextInt(3);
        int height = 2 + random.nextInt(3);
        boolean alongX = random.nextBoolean();
        long shapeSeed = random.nextLong();
        for (int x = -radiusX; x <= radiusX + height / 2; x++) {
            for (int y = -1; y <= height; y++) {
                for (int z = -radiusZ; z <= radiusZ + height / 2; z++) {
                    if (!RegionalLandmarkShape.angularOutcrop(
                        shapeSeed,
                        x,
                        y,
                        z,
                        radiusX,
                        radiusZ,
                        height,
                        alongX
                    )) {
                        continue;
                    }
                    level.setBlock(base.offset(x, y, z), block.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    private static boolean buildFallenLog(
        WorldGenLevel level,
        BlockPos base,
        RandomSource random,
        Block logBlock
    ) {
        boolean alongX = random.nextBoolean();
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        BlockState log = logBlock.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, axis);
        int length = 6 + random.nextInt(6);
        for (int i = -length / 2; i <= length / 2; i++) {
            int x = base.getX() + (alongX ? i : 0);
            int z = base.getZ() + (alongX ? 0 : i);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos position = new BlockPos(x, y, z);
            if (level.getBlockState(position).canBeReplaced()) {
                level.setBlock(position, log, 2);
                if (random.nextInt(3) == 0 && level.isEmptyBlock(position.above())) {
                    level.setBlock(position.above(), Blocks.MOSS_CARPET.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    private static boolean buildMeadowOutcrop(
        WorldGenLevel level,
        BlockPos base,
        RandomSource random
    ) {
        buildBoulder(level, base, random, random.nextBoolean() ? Blocks.TUFF : Blocks.COBBLESTONE);
        for (int i = 0; i < 12; i++) {
            BlockPos flower = base.offset(
                random.nextInt(11) - 5,
                0,
                random.nextInt(11) - 5
            );
            int y = level.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG,
                flower.getX(),
                flower.getZ()
            );
            flower = new BlockPos(flower.getX(), y, flower.getZ());
            BlockState state = WorldgenBlocks.rareFlower(level, flower, random.nextInt(32));
            if (level.isEmptyBlock(flower) && state.canSurvive(level, flower)) {
                level.setBlock(flower, state, 2);
            }
        }
        return true;
    }
}
