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
            return Placement.withoutLookup(false);
        }
        requireRuntimeInputs(
            level,
            base,
            plan.species(),
            plan.quality(),
            plan.environment(),
            plan.envelope()
        );
        CheckResult envelopeCheck = checkEnvelope(level, base, plan.envelope());
        TreePlacementProbeTelemetry.recordEnvelope(envelopeCheck.probes());
        if (!envelopeCheck.allowed()) {
            return Placement.withoutLookup(false);
        }

        TreeModelCache.Lookup lookup = TreeModelCache.getOrCreateDetailed(plan);
        VoxelTreeModel model = lookup.model();
        CheckResult finalCheck = checkModelPlacement(level, base, model);
        TreePlacementProbeTelemetry.recordFinal(finalCheck.probes());
        if (!finalCheck.allowed()) {
            return Placement.afterLookup(false, lookup.cacheHit());
        }
        placeModel(level, base, model, plan.species().materialProfile());
        return Placement.afterLookup(true, lookup.cacheHit());
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
        CheckResult envelopeCheck = checkEnvelope(level, base, envelope);
        TreePlacementProbeTelemetry.recordEnvelope(envelopeCheck.probes());
        if (!envelopeCheck.allowed()) {
            return false;
        }

        VoxelTreeModel model = TreeGenerationPipeline.generate(
            seed,
            species,
            quality,
            environment
        ).model();
        CheckResult finalCheck = checkModelPlacement(level, base, model);
        TreePlacementProbeTelemetry.recordFinal(finalCheck.probes());
        if (!finalCheck.allowed()) {
            return false;
        }
        placeModel(level, base, model, species.materialProfile());
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

    private static CheckResult checkEnvelope(
        WorldGenLevel level,
        BlockPos base,
        TreePlacementEnvelope envelope
    ) {
        int probes = 0;
        BlockPos ground = base.below();
        probes++;
        if (level.getBlockState(ground).isAir()) {
            return new CheckResult(false, probes);
        }

        int verticalProbes = Math.max(3, envelope.probeCount() / 3);
        for (int index = 0; index < verticalProbes; index++) {
            int y = (int)Math.round(
                envelope.height() * index / (double)(verticalProbes - 1)
            );
            probes++;
            if (!probeReplaceable(level, base.above(y))) {
                return new CheckResult(false, probes);
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
            probes++;
            if (!probeReplaceable(level, probe)) {
                return new CheckResult(false, probes);
            }
        }
        return new CheckResult(true, probes);
    }

    private static boolean probeReplaceable(
        WorldGenLevel level,
        BlockPos position
    ) {
        if (!withinBuildHeight(level, position)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        return state.getFluidState().isEmpty() && state.canBeReplaced();
    }

    private static CheckResult checkModelPlacement(
        WorldGenLevel level,
        BlockPos base,
        VoxelTreeModel model
    ) {
        int probes = 0;
        BlockPos ground = base.below();
        probes++;
        if (level.getBlockState(ground).isAir()) {
            return new CheckResult(false, probes);
        }
        for (VoxelTreeModel.Voxel voxel : model.wood()) {
            BlockPos position = absolute(base, voxel);
            probes++;
            if (!withinBuildHeight(level, position)) {
                return new CheckResult(false, probes);
            }
            BlockState state = level.getBlockState(position);
            if (!state.getFluidState().isEmpty()
                || !woodPositionAvailable(state, voxel.y())) {
                return new CheckResult(false, probes);
            }
        }
        for (VoxelTreeModel.Voxel voxel : model.leaves()) {
            BlockPos position = absolute(base, voxel);
            probes++;
            if (!withinBuildHeight(level, position)) {
                return new CheckResult(false, probes);
            }
            BlockState state = level.getBlockState(position);
            if (!state.getFluidState().isEmpty() || !state.canBeReplaced()) {
                return new CheckResult(false, probes);
            }
        }
        return new CheckResult(true, probes);
    }

    private static boolean woodPositionAvailable(
        BlockState state,
        int relativeY
    ) {
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
        VoxelTreeModel model,
        TreeMaterialProfile materials
    ) {
        TreeMaterialResolver.ResolvedMaterials resolved =
            TreeMaterialResolver.resolve(materials);
        Set<VoxelTreeModel.Voxel> wood = new HashSet<>(model.wood());
        for (VoxelTreeModel.Voxel voxel : model.wood()) {
            BlockState log = resolved.logBlock().defaultBlockState();
            if (log.hasProperty(RotatedPillarBlock.AXIS)) {
                log = log.setValue(
                    RotatedPillarBlock.AXIS,
                    dominantAxis(voxel, wood)
                );
            }
            level.setBlock(absolute(base, voxel), log, 2);
        }
        BlockState leaves = resolved.leavesBlock().defaultBlockState();
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

    private record CheckResult(boolean allowed, int probes) {
        private CheckResult {
            if (probes < 0) {
                throw new IllegalArgumentException("tree probe count cannot be negative");
            }
        }
    }

    public record Placement(
        boolean placed,
        boolean cacheLookupPerformed,
        boolean cacheHit
    ) {
        public Placement {
            if (cacheHit && !cacheLookupPerformed) {
                throw new IllegalArgumentException(
                    "cache hit requires a performed lookup"
                );
            }
        }

        public static Placement withoutLookup(boolean placed) {
            return new Placement(placed, false, false);
        }

        public static Placement afterLookup(boolean placed, boolean cacheHit) {
            return new Placement(placed, true, cacheHit);
        }
    }
}
