package dev.nexusmc.landscape.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Builds small gravel point bars and driftwood inside existing river biomes.
 * The terrain density function shapes the valley; this feature gives its banks
 * the depositional details seen at real bends and confluences.
 */
public final class RiverBankFeature extends Feature<NoneFeatureConfiguration> {
    public RiverBankFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!level.getFluidState(origin).is(FluidTags.WATER)
            && !level.getFluidState(origin.above()).is(FluidTags.WATER)) {
            return false;
        }

        boolean alongX = random.nextBoolean();
        int length = 6 + random.nextInt(7);
        int changed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int longitudinal = -length; longitudinal <= length; longitudinal++) {
            int halfWidth = Math.max(1, 4 - Math.abs(longitudinal) * 3 / length);
            for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
                int x = alongX ? longitudinal : lateral;
                int z = alongX ? lateral : longitudinal;
                cursor.set(origin.getX() + x, origin.getY(), origin.getZ() + z);
                if (!level.getFluidState(cursor).is(FluidTags.WATER)
                    && !level.getFluidState(cursor.above()).is(FluidTags.WATER)) {
                    continue;
                }

                BlockState sediment = random.nextInt(7) == 0
                    ? Blocks.CLAY.defaultBlockState()
                    : random.nextInt(4) == 0
                        ? Blocks.SAND.defaultBlockState()
                        : Blocks.GRAVEL.defaultBlockState();
                level.setBlock(cursor, sediment, 2);
                if (Math.abs(lateral) <= 1 && random.nextInt(4) == 0) {
                    level.setBlock(cursor.above(), sediment, 2);
                }
                changed++;
            }
        }

        if (changed > 12 && random.nextInt(3) == 0) {
            placeDriftwood(level, origin.above(), alongX, random);
        }
        return changed > 8;
    }

    private static void placeDriftwood(
        WorldGenLevel level,
        BlockPos start,
        boolean alongX,
        RandomSource random
    ) {
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        BlockState log = Blocks.STRIPPED_OAK_LOG.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, axis);
        int length = 3 + random.nextInt(4);
        for (int i = 0; i < length; i++) {
            BlockPos pos = alongX ? start.offset(i - length / 2, 0, 0)
                : start.offset(0, 0, i - length / 2);
            if (level.getBlockState(pos).canBeReplaced()
                || level.getFluidState(pos).is(FluidTags.WATER)) {
                level.setBlock(pos, log, 2);
            }
        }
    }
}
