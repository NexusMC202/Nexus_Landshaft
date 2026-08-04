package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bridges pure tree models into Minecraft world placement. A cheap conservative
 * envelope is checked before a complete model is requested from the strictly
 * bounded deterministic cache.
 */
public final class ProceduralTreeRuntime {
    public static final String ENABLE_PROPERTY = "nexus_landscape.tree_v2";

    private ProceduralTreeRuntime() {
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "true"));
    }

    public static boolean placeConifer(
        WorldGenLevel level,
        BlockPos base,
        ProceduralTreePlan plan
    ) {
        return placeConiferDetailed(level, base, plan).placed();
    }

    public static Placement placeConiferDetailed(
        WorldGenLevel level,
        BlockPos base,
        ProceduralTreePlan plan
    ) {
        if (plan == null) {
            throw new IllegalArgumentException("tree plan is required");
        }
        if (!enabled()) {
            return new Placement(false, false);
        }
        requireRuntimeInputs(
            level,
            base,
            plan.species(),
            plan.quality(),
            plan.environment(),
            plan.envelope()
        );
        if (!canFitEnvelope(level, base, plan.envelope())) {
            return new Placement(false, false);
        }

        TreeModelCache.Lookup lookup = TreeModelCache.getOrCreateDetailed(plan);
        VoxelTreeModel model = lookup.model();
        if (!canPlace(level, base, model)) {
            return new Placement(false, lookup.cacheHit());
        }
        placeModel(level, base, model);
        return new Placement(true, lookup.cacheHit());
    }

    /** Compatibility overload for older tests and callers; resolves as spruce. */
    public static boolean placeConifer(
        WorldGenLevel level,
        BlockPos base,
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        TreePlacementEnvelope envelope = TreePlacementEnvelope.estimate(
            ConiferSpeciesProfile.SPRUCE,
            quality,
            environment,
            false
        );
        return placeConiferUncached(
            level,
            base,
            seed,
            ConiferSpeciesProfile.SPRUCE,
            quality,
            environment,
            envelope
        );
    }

    private static boolean placeConiferUncached(
        WorldGenLevel level,
        BlockPos base,
        long seed,
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreePlacementEnvelope envelope
    ) {
        if (!enabled()) {
            return false;
        }
        requireRuntimeInputs(level, base, species, quality, environment, envelope);
        if (!canFitEnvelope(level, base, envelope)) {
            return false;
        }

        TreeLifeHistory history = TreeLifeHistory.generate(seed, quality, environment);
        BranchGraph graph = SpeciesConiferBranchGenerator.generate(
            seed, species, quality, environment, history
        );
        VoxelTreeModel model = TreeVoxelizer.voxelize(
            graph, quality, seed, species
        );
        if (!canPlace(level, base, model)) {
            return false;
        }
        placeModel(level, base, model);
        return true;
    }

    private static void requireRuntimeInputs(
        WorldGenLevel level,
        BlockPos base,
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreePlacementEnvelope envelope
    ) {
        if (level == null || base == null || species == null || quality == null
            || environment == null || envelope == null) {
            throw new IllegalArgumentException("runtime tree inputs are required");
        }
    }

    private static boolean canFitEnvelope(
        WorldGenLevel level,
        BlockPos base,
        TreePlacementEnvelope envelope
    ) {
        BlockPos ground = base.below();
        if (level.getBlockState(ground).isAir()
            || !level.getFluidState(base).isEmpty()) {
            return false;
        }

        int verticalProbes = Math.max(3, envelope.probeCount() / 3);
        for (int index = 0; index < verticalProbes; index++) {
            int y = (int)Math.round(
                envelope.height() * index / (double)(verticalProbes - 1)
            );
            if (!probeReplaceable(level, base.above(y))) {
                return false;
            }
        }

        int radialProbes = envelope.probeCount() - verticalProbes;
        int crownY = Math.max(2, (int)Math.round(envelope.height() * 0.62));
        for (int index = 0; index < radialProbes; index++) {
            double angle = Math.PI * 2.0 * index / Math.max(1, radialProbes);
            int radius = index % 2 == 0
                ? envelope.horizontalRadius()
                : Math.max(1, envelope.horizontalRadius() / 2);
            BlockPos probe = base.offset(
                (int)Math.round(Math.cos(angle) * radius),
                crownY + (index % 3 - 1),
                (int)Math.round(Math.sin(angle) * radius)
            );
            if (!probeReplaceable(level, probe)) {
                return false;
            }
        }
        return true;
    }

    private static boolean probeReplaceable(
        WorldGenLevel level,
        BlockPos position
    ) {
        return withinBuildHeight(level, position)
            && level.getFluidState(position).isEmpty()
            && level.getBlockState(position).canBeReplaced();
    }

    private static boolean canPlace(
        WorldGenLevel level,
        BlockPos base,
        VoxelTreeModel model
    ) {
        BlockPos ground = base.below();
        if (level.getBlockState(ground).isAir()
            || !level.getFluidState(base).isEmpty()) {
            return false;
        }
        for (VoxelTreeModel.Voxel voxel : model.wood()) {
            BlockPos position = absolute(base, voxel);
            if (!withinBuildHeight(level, position)
                || !level.getFluidState(position).isEmpty()
                || !woodPositionAvailable(level, position, voxel.y())) {
                return false;
            }
        }
        for (VoxelTreeModel.Voxel voxel : model.leaves()) {
            BlockPos position = absolute(base, voxel);
            if (!withinBuildHeight(level, position)
                || !level.getFluidState(position).isEmpty()
                || !level.getBlockState(position).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static boolean woodPositionAvailable(
        WorldGenLevel level,
        BlockPos position,
        int relativeY
    ) {
        BlockState state = level.getBlockState(position);
        if (state.canBeReplaced()) {
            return true;
        }
        return relativeY < 0 && naturalRootSoil(state);
    }

    private static boolean naturalRootSoil(BlockState state) {
        return state.is(Blocks.DIRT)
            || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.ROOTED_DIRT)
            || state.is(Blocks.MYCELIUM)
            || state.is(Blocks.MOSS_BLOCK)
            || state.is(Blocks.MUD)
            || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
            || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean withinBuildHeight(WorldGenLevel level, BlockPos position) {
        return position.getY() >= level.getMinBuildHeight()
            && position.getY() < level.getMaxBuildHeight();
    }

    private static void placeModel(
        WorldGenLevel level,
        BlockPos base,
        VoxelTreeModel model
    ) {
        Set<VoxelTreeModel.Voxel> wood = new HashSet<>(model.wood());
        for (VoxelTreeModel.Voxel voxel : model.wood()) {
            BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
            if (log.hasProperty(RotatedPillarBlock.AXIS)) {
                log = log.setValue(
                    RotatedPillarBlock.AXIS,
                    dominantAxis(voxel, wood)
                );
            }
            level.setBlock(absolute(base, voxel), log, 2);
        }
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState();
        if (leaves.hasProperty(LeavesBlock.PERSISTENT)) {
            leaves = leaves.setValue(LeavesBlock.PERSISTENT, true);
        }
        for (VoxelTreeModel.Voxel voxel : model.leaves()) {
            level.setBlock(absolute(base, voxel), leaves, 2);
        }
    }

    private static Direction.Axis dominantAxis(
        VoxelTreeModel.Voxel voxel,
        Set<VoxelTreeModel.Voxel> wood
    ) {
        int x = neighbors(voxel, wood, 1, 0, 0);
        int y = neighbors(voxel, wood, 0, 1, 0);
        int z = neighbors(voxel, wood, 0, 0, 1);
        if (x > y && x >= z) {
            return Direction.Axis.X;
        }
        if (z > y && z > x) {
            return Direction.Axis.Z;
        }
        return Direction.Axis.Y;
    }

    private static int neighbors(
        VoxelTreeModel.Voxel voxel,
        Set<VoxelTreeModel.Voxel> wood,
        int dx,
        int dy,
        int dz
    ) {
        int result = 0;
        if (wood.contains(new VoxelTreeModel.Voxel(
            voxel.x() + dx, voxel.y() + dy, voxel.z() + dz
        ))) {
            result++;
        }
        if (wood.contains(new VoxelTreeModel.Voxel(
            voxel.x() - dx, voxel.y() - dy, voxel.z() - dz
        ))) {
            result++;
        }
        return result;
    }

    private static BlockPos absolute(
        BlockPos base,
        VoxelTreeModel.Voxel voxel
    ) {
        return base.offset(voxel.x(), voxel.y(), voxel.z());
    }

    public record Placement(boolean placed, boolean cacheHit) {
    }
}
