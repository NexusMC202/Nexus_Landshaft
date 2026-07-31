package dev.nexusmc.landscape.worldgen.v2.vegetation;

import dev.nexusmc.landscape.diagnostics.Stage6Profiler;
import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.NexusV2HydrologySampler;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContext;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContextFactory;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceNoise;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Coordinate-driven vegetation accents applied after vanilla decoration.
 * Broad forest/clearing fields cross chunk boundaries; candidate ownership is
 * based on absolute lattice cells, not mutable random or generation order.
 */
public final class VegetationProvincePass {
    private static final Map<String, BlockState> GROUND = groundStates();
    private static final Map<RandomState, Telemetry> TELEMETRY =
        Collections.synchronizedMap(new WeakHashMap<>());

    private VegetationProvincePass() {
    }

    public static void apply(
        WorldGenLevel level,
        ChunkAccess chunk,
        RandomState randomState
    ) {
        long seed = level.getSeed();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        NexusV2FieldSampler fields = new NexusV2FieldSampler(randomState);
        NexusV2HydrologySampler hydrology =
            new NexusV2HydrologySampler(randomState);
        Telemetry telemetry = telemetry(randomState);
        telemetry.chunks.increment();

        for (int localZ = 1; localZ < 16; localZ += 4) {
            for (int localX = 1; localX < 16; localX += 4) {
                int worldX = minX + localX + jitter(seed, minX + localX, minZ + localZ, 11L);
                int worldZ = minZ + localZ + jitter(seed, minX + localX, minZ + localZ, 17L);
                decorateGround(
                    level,
                    fields,
                    hydrology,
                    seed,
                    worldX,
                    worldZ,
                    telemetry
                );
            }
        }
        placeOwnedTrees(level, chunk, fields, hydrology, seed, telemetry);
        decorateCaves(level, chunk, seed, telemetry);
    }

    public static String snapshotAndReset(RandomState randomState) {
        return snapshot(randomState, true);
    }

    public static String snapshot(RandomState randomState) {
        return snapshot(randomState, false);
    }

    public static void reset(RandomState randomState) {
        snapshot(randomState, true);
    }

    private static String snapshot(RandomState randomState, boolean reset) {
        Telemetry telemetry = telemetry(randomState);
        return "vegetation.chunks=" + value(telemetry.chunks, reset) + '\n'
            + "vegetation.ground_attempts=" + value(telemetry.groundAttempts, reset) + '\n'
            + "vegetation.ground_placed=" + value(telemetry.ground, reset) + '\n'
            + "vegetation.rock_blocks=" + value(telemetry.rocks, reset) + '\n'
            + "vegetation.tree_attempts=" + value(telemetry.treeAttempts, reset) + '\n'
            + "vegetation.tree_placed=" + value(telemetry.trees, reset) + '\n'
            + "vegetation.tree_rejected_slope=" + value(telemetry.treeRejectedSlope, reset) + '\n'
            + "vegetation.tree_rejected_water=" + value(telemetry.treeRejectedWater, reset) + '\n'
            + "vegetation.tree_rejected_river=" + value(telemetry.treeRejectedRiver, reset) + '\n'
            + "vegetation.tree_rejected_altitude=" + value(telemetry.treeRejectedAltitude, reset) + '\n'
            + "vegetation.tree_rejected_density=" + value(telemetry.treeRejectedDensity, reset) + '\n'
            + "vegetation.tree_rejected_other=" + value(telemetry.treeRejectedOther, reset) + '\n'
            + provinceSnapshot(telemetry, reset)
            + "cave.columns_or_sections_processed=" + value(telemetry.caveColumns, reset) + '\n'
            + "cave.blocks_changed=" + value(telemetry.caves, reset) + '\n'
            + "cave.profile.lush=" + value(telemetry.caveLush, reset) + '\n'
            + "cave.profile.dripstone=" + value(telemetry.caveDripstone, reset) + '\n'
            + "cave.profile.deep_dark=" + value(telemetry.caveDeepDark, reset) + '\n'
            + "cave.profile.generic=" + value(telemetry.caveGeneric, reset) + '\n';
    }

    private static long value(LongAdder counter, boolean reset) {
        return reset ? counter.sumThenReset() : counter.sum();
    }

    private static void decorateGround(
        WorldGenLevel level,
        NexusV2FieldSampler fields,
        NexusV2HydrologySampler hydrology,
        long seed,
        int x,
        int z,
        Telemetry telemetry
    ) {
        telemetry.groundAttempts.increment();
        Sample sample = sample(level, fields, hydrology, seed, x, z);
        if (sample == null || !sample.selection().terrestrialAllowed()) {
            return;
        }
        telemetry.provinces[classifyProvince(sample).ordinal()].increment();
        BlockPos position = new BlockPos(x, sample.surfaceY() + 1, z);
        if (!level.isEmptyBlock(position)) {
            return;
        }
        double roll = unit(seed, x, z, 0x6A701L);
        VegetationSelection selection = sample.selection();
        if (roll < selection.rockDensity() * 0.18) {
            level.setBlock(
                position,
                sample.context().surface().volcanicWeight() > 0.55
                    ? Blocks.BLACKSTONE.defaultBlockState()
                    : Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                2
            );
            telemetry.rocks.increment();
            return;
        }
        if (roll > selection.groundDensity()) {
            return;
        }
        BlockState state = chooseGround(
            sample.profile(),
            seed,
            x,
            z,
            roll < selection.flowerDensity()
        );
        if (state.canSurvive(level, position)) {
            level.setBlock(position, state, 2);
            telemetry.ground.increment();
        }
    }

    private static void placeOwnedTrees(
        WorldGenLevel level,
        ChunkAccess chunk,
        NexusV2FieldSampler fields,
        NexusV2HydrologySampler hydrology,
        long seed,
        Telemetry telemetry
    ) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int cellMinX = Math.floorDiv(minX - 8, 14);
        int cellMaxX = Math.floorDiv(minX + 23, 14);
        int cellMinZ = Math.floorDiv(minZ - 8, 14);
        int cellMaxZ = Math.floorDiv(minZ + 23, 14);
        for (int cellZ = cellMinZ; cellZ <= cellMaxZ; cellZ++) {
            for (int cellX = cellMinX; cellX <= cellMaxX; cellX++) {
                int x = cellX * 14 + 3
                    + (int)Math.floor(unit(seed, cellX, cellZ, 0x71EEL) * 8.0);
                int z = cellZ * 14 + 3
                    + (int)Math.floor(unit(seed, cellX, cellZ, 0x71EFL) * 8.0);
                if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15) {
                    continue;
                }
                telemetry.treeAttempts.increment();
                Sample sample = sample(level, fields, hydrology, seed, x, z);
                if (sample == null) {
                    telemetry.treeRejectedOther.increment();
                    continue;
                }
                if (!sample.selection().treesAllowed()) {
                    recordTreeRejection(sample, telemetry);
                    continue;
                }
                if (unit(seed, x, z, 0x7AEE1L)
                    >= sample.selection().treeDensity()) {
                    telemetry.treeRejectedDensity.increment();
                    continue;
                }
                VegetationProfile.TreeShape shape = sample.profile().treeShapes()
                    .get(Math.floorMod(
                        SurfaceNoise.hash(seed, x, z, 0x5A9EL),
                        sample.profile().treeShapes().size()
                    ));
                long placementStarted = Stage6Profiler.start();
                boolean placed = buildTree(
                    level,
                    new BlockPos(x, sample.surfaceY() + 1, z),
                    shape,
                    unit(seed, x, z, 0x01D6L)
                        < sample.selection().oldGrowthDensity()
                );
                Stage6Profiler.record(
                    Stage6Profiler.Phase.VEGETATION_BLOCK_PLACEMENT,
                    placementStarted
                );
                if (placed) {
                    telemetry.trees.increment();
                } else {
                    telemetry.treeRejectedOther.increment();
                }
            }
        }
    }

    private static Sample sample(
        WorldGenLevel level,
        NexusV2FieldSampler fields,
        NexusV2HydrologySampler hydrology,
        long seed,
        int x,
        int z
    ) {
        long samplingStarted = Stage6Profiler.start();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surfaceY <= level.getMinBuildHeight() + 4) {
            return null;
        }
        BlockPos surface = new BlockPos(x, surfaceY, z);
        long biomeStarted = Stage6Profiler.start();
        Holder<Biome> biome = level.getBiome(surface);
        String biomeId = biome.unwrapKey()
            .map(key -> key.location().toString())
            .orElse("nexus_landscape:unknown");
        Stage6Profiler.record(
            Stage6Profiler.Phase.VEGETATION_BIOME_LOOKUP,
            biomeStarted
        );
        long contextStarted = Stage6Profiler.start();
        NexusV2FieldSampler.SurfaceInputs input = fields.surfaceInputs(x, z);
        RegionalFieldMath.Sample regional = SurfaceContextFactory.regional(input);
        HydrologyMath.Sample river = hydrology.sample(x, z);
        HydrologyMath.BasinSample basin = hydrology.basinSample(x, z);
        double dx = fields.analyticalTerrain(x + 4, z).surfaceY()
            - fields.analyticalTerrain(x - 4, z).surfaceY();
        double dz = fields.analyticalTerrain(x, z + 4).surfaceY()
            - fields.analyticalTerrain(x, z - 4).surfaceY();
        double slope = Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / 32.0);
        SurfaceContext surfaceContext = SurfaceContextFactory.create(
            seed,
            x,
            z,
            surfaceY,
            biomeId,
            input,
            regional,
            river,
            basin,
            slope
        );
        VegetationProfile profile = VegetationProfileCatalog.find(biomeId)
            .filter(candidate -> candidate.terrestrial())
            .orElseGet(() -> VegetationProfileCatalog.fallback(
                input.temperature(),
                input.humidity(),
                false
            ));
        boolean water = !level.getFluidState(surface).isEmpty()
            || !level.getFluidState(surface.above()).isEmpty();
        VegetationContext context = new VegetationContext(
            surfaceContext,
            SurfaceNoise.value(seed, x, z, 176, 0xF0AE57L),
            SurfaceNoise.value(seed, x, z, 104, 0xC1EA41L),
            false,
            water
        );
        Sample result = new Sample(
            surfaceY,
            profile,
            context,
            VegetationResolver.resolve(profile, context)
        );
        Stage6Profiler.record(
            Stage6Profiler.Phase.VEGETATION_CONTEXT_PREPARATION,
            contextStarted
        );
        Stage6Profiler.record(
            Stage6Profiler.Phase.VEGETATION_SAMPLING,
            samplingStarted
        );
        return result;
    }

    private static void decorateCaves(
        WorldGenLevel level,
        ChunkAccess chunk,
        long seed,
        Telemetry telemetry
    ) {
        long caveStarted = Stage6Profiler.start();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int candidate = 0; candidate < 4; candidate++) {
            int x = minX + 2 + (int)Math.floor(
                unit(seed, chunk.getPos().x, chunk.getPos().z,
                    0xCA7E00L + candidate * 2L) * 12.0
            );
            int z = minZ + 2 + (int)Math.floor(
                unit(seed, chunk.getPos().x, chunk.getPos().z,
                    0xCA7E01L + candidate * 2L) * 12.0
            );
            telemetry.caveColumns.increment();
            int topY = Math.min(
                96,
                level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 8
            );
            int verticalOffset = (int)Math.floor(
                unit(seed, x, z, 0xCA7E5L) * 4.0
            );
            for (int y = level.getMinBuildHeight() + 8 + verticalOffset;
                 y <= topY;
                 y += 4) {
                    cursor.set(x, y, z);
                    String biomeId = level.getBiome(cursor).unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("");
                    VegetationProfile profile =
                        VegetationProfileCatalog.find(biomeId).orElse(null);
                    if (profile == null) {
                        if (!biomeId.isEmpty()
                            && !biomeId.startsWith("minecraft:")) {
                            telemetry.caveGeneric.increment();
                        }
                        continue;
                    }
                    if (profile.terrestrial()) {
                        continue;
                    }
                    switch (profile.family()) {
                        case LUSH_CAVE -> telemetry.caveLush.increment();
                        case DRIPSTONE_CAVE -> telemetry.caveDripstone.increment();
                        case DEEP_DARK -> telemetry.caveDeepDark.increment();
                        default -> telemetry.caveGeneric.increment();
                    }
                    if (!level.isEmptyBlock(cursor)
                        || level.getBlockState(cursor.below()).isAir()) {
                        continue;
                    }
                    BlockState accent = switch (profile.family()) {
                        case LUSH_CAVE -> Blocks.MOSS_CARPET.defaultBlockState();
                        case DRIPSTONE_CAVE -> Blocks.POINTED_DRIPSTONE.defaultBlockState();
                        case DEEP_DARK -> Blocks.SCULK_VEIN.defaultBlockState();
                        default -> Blocks.AIR.defaultBlockState();
                    };
                    if (!accent.isAir()
                        && unit(seed, x, z, y * 31L) < 0.42
                        && accent.canSurvive(level, cursor)) {
                        level.setBlock(cursor, accent, 2);
                        telemetry.caves.increment();
                    }
                }
        }
        Stage6Profiler.record(Stage6Profiler.Phase.CAVE_ACCENTS, caveStarted);
    }

    private static boolean buildTree(
        WorldGenLevel level,
        BlockPos base,
        VegetationProfile.TreeShape shape,
        boolean oldGrowth
    ) {
        Block logBlock = switch (shape) {
            case BIRCH_COLUMN -> Blocks.BIRCH_LOG;
            case SPRUCE_CONICAL, PINE_TALL -> Blocks.SPRUCE_LOG;
            case ACACIA_FLAT -> Blocks.ACACIA_LOG;
            case JUNGLE_EMERGENT -> Blocks.JUNGLE_LOG;
            case CHERRY_TERRACE -> Blocks.CHERRY_LOG;
            case MANGROVE_ROOTED -> Blocks.MANGROVE_LOG;
            case DARK_OAK_BROAD -> Blocks.DARK_OAK_LOG;
            case GIANT_MUSHROOM -> Blocks.MUSHROOM_STEM;
            default -> Blocks.OAK_LOG;
        };
        Block leavesBlock = switch (shape) {
            case BIRCH_COLUMN -> Blocks.BIRCH_LEAVES;
            case SPRUCE_CONICAL, PINE_TALL -> Blocks.SPRUCE_LEAVES;
            case ACACIA_FLAT -> Blocks.ACACIA_LEAVES;
            case JUNGLE_EMERGENT -> Blocks.JUNGLE_LEAVES;
            case CHERRY_TERRACE -> Blocks.CHERRY_LEAVES;
            case MANGROVE_ROOTED -> Blocks.MANGROVE_LEAVES;
            case DARK_OAK_BROAD -> Blocks.DARK_OAK_LEAVES;
            case GIANT_MUSHROOM -> Blocks.RED_MUSHROOM_BLOCK;
            default -> Blocks.OAK_LEAVES;
        };
        int height = (oldGrowth ? 9 : 6) + switch (shape) {
            case PINE_TALL, JUNGLE_EMERGENT -> 4;
            case BIRCH_COLUMN -> 2;
            default -> 0;
        };
        if (!level.getFluidState(base).isEmpty()
            || !level.getBlockState(base).canBeReplaced()) {
            return false;
        }
        for (int y = 0; y < height; y++) {
            BlockPos position = base.above(y);
            if (!level.getBlockState(position).canBeReplaced()) {
                return false;
            }
        }
        BlockState log = logBlock.defaultBlockState();
        if (log.hasProperty(RotatedPillarBlock.AXIS)) {
            log = log.setValue(
                RotatedPillarBlock.AXIS,
                net.minecraft.core.Direction.Axis.Y
            );
        }
        for (int y = 0; y < height; y++) {
            level.setBlock(base.above(y), log, 2);
        }
        BlockState leaves = leavesBlock.defaultBlockState();
        if (leaves.hasProperty(LeavesBlock.PERSISTENT)) {
            leaves = leaves.setValue(LeavesBlock.PERSISTENT, true);
        }
        int radius = switch (shape) {
            case ACACIA_FLAT, DARK_OAK_BROAD, CHERRY_TERRACE -> 3;
            case GIANT_MUSHROOM -> 3;
            default -> 2;
        };
        BlockPos crown = base.above(height);
        for (int dy = -2; dy <= 1; dy++) {
            int layerRadius = dy == 1 ? Math.max(1, radius - 1) : radius;
            for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                    if (dx * dx + dz * dz > layerRadius * layerRadius + 1) {
                        continue;
                    }
                    BlockPos position = crown.offset(dx, dy, dz);
                    if (level.getBlockState(position).canBeReplaced()) {
                        level.setBlock(position, leaves, 2);
                    }
                }
            }
        }
        return true;
    }

    private static void recordTreeRejection(
        Sample sample,
        Telemetry telemetry
    ) {
        SurfaceContext surface = sample.context().surface();
        if (sample.context().waterAtSurface()) {
            telemetry.treeRejectedWater.increment();
        } else if (Math.max(surface.riverMask(), surface.riverInfluence()) >= 0.10) {
            telemetry.treeRejectedRiver.increment();
        } else if (surface.slope() >= sample.profile().maxTreeSlope() * 0.55) {
            telemetry.treeRejectedSlope.increment();
        } else if (surface.surfaceY() >= sample.profile().treeLineY() - 28) {
            telemetry.treeRejectedAltitude.increment();
        } else {
            telemetry.treeRejectedOther.increment();
        }
    }

    private static Province classifyProvince(Sample sample) {
        SurfaceContext surface = sample.context().surface();
        if (surface.coastWeight() > 0.34 && surface.surfaceY() <= 104) {
            return Province.COASTAL;
        }
        if (surface.alpineExposure()
            || surface.surfaceY() > sample.profile().treeLineY() - 20) {
            return Province.ALPINE;
        }
        if (surface.slope() > 0.58) {
            return Province.ROCKY_SLOPE;
        }
        if (surface.groundwater() > 0.68
            || surface.wetBank()) {
            return Province.WET_LOWLAND;
        }
        if (sample.context().clearingNoise() < sample.profile().clearingShare()) {
            return Province.CLEARING;
        }
        if (sample.context().forestCore() > 0.72) {
            return Province.DENSE_FOREST;
        }
        if (sample.selection().treeDensity() > 0.08) {
            return Province.WOODLAND;
        }
        return Province.OPEN_VALLEY;
    }

    private static String provinceSnapshot(Telemetry telemetry, boolean reset) {
        StringBuilder output = new StringBuilder();
        for (Province province : Province.values()) {
            output.append("vegetation.province.")
                .append(province.name().toLowerCase(java.util.Locale.ROOT))
                .append('=')
                .append(value(telemetry.provinces[province.ordinal()], reset))
                .append('\n');
        }
        return output.toString();
    }

    private static BlockState chooseGround(
        VegetationProfile profile,
        long seed,
        int x,
        int z,
        boolean flower
    ) {
        if (flower) {
            return switch (profile.family()) {
                case MEADOW -> Blocks.AZURE_BLUET.defaultBlockState();
                case TROPICAL_HUMID, WETLAND -> Blocks.BLUE_ORCHID.defaultBlockState();
                default -> Blocks.DANDELION.defaultBlockState();
            };
        }
        String[] ids = profile.groundPalette().toArray(String[]::new);
        String id = ids[Math.floorMod(
            SurfaceNoise.hash(seed, x, z, 0x6A0D1L),
            ids.length
        )];
        return GROUND.getOrDefault(id, Blocks.SHORT_GRASS.defaultBlockState());
    }

    private static int jitter(long seed, int x, int z, long salt) {
        return (int)Math.floor(unit(seed, x, z, salt) * 3.0) - 1;
    }

    private static double unit(long seed, int x, int z, long salt) {
        return (SurfaceNoise.hash(seed, x, z, salt) >>> 11) * 0x1.0p-53;
    }

    private static Map<String, BlockState> groundStates() {
        Map<String, BlockState> result = new HashMap<>();
        for (VegetationProfile profile : VegetationProfileCatalog.profiles().values()) {
            for (String id : profile.groundPalette()) {
                BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
                    .ifPresent(block -> result.put(id, block.defaultBlockState()));
            }
        }
        return Map.copyOf(result);
    }

    private static Telemetry telemetry(RandomState randomState) {
        synchronized (TELEMETRY) {
            return TELEMETRY.computeIfAbsent(
                randomState,
                ignored -> new Telemetry()
            );
        }
    }

    private static final class Telemetry {
        private final LongAdder chunks = new LongAdder();
        private final LongAdder groundAttempts = new LongAdder();
        private final LongAdder ground = new LongAdder();
        private final LongAdder rocks = new LongAdder();
        private final LongAdder treeAttempts = new LongAdder();
        private final LongAdder trees = new LongAdder();
        private final LongAdder treeRejectedSlope = new LongAdder();
        private final LongAdder treeRejectedWater = new LongAdder();
        private final LongAdder treeRejectedRiver = new LongAdder();
        private final LongAdder treeRejectedAltitude = new LongAdder();
        private final LongAdder treeRejectedDensity = new LongAdder();
        private final LongAdder treeRejectedOther = new LongAdder();
        private final LongAdder[] provinces = new LongAdder[Province.values().length];
        private final LongAdder caveColumns = new LongAdder();
        private final LongAdder caves = new LongAdder();
        private final LongAdder caveLush = new LongAdder();
        private final LongAdder caveDripstone = new LongAdder();
        private final LongAdder caveDeepDark = new LongAdder();
        private final LongAdder caveGeneric = new LongAdder();

        private Telemetry() {
            for (int index = 0; index < provinces.length; index++) {
                provinces[index] = new LongAdder();
            }
        }
    }

    private enum Province {
        DENSE_FOREST,
        WOODLAND,
        CLEARING,
        OPEN_VALLEY,
        WET_LOWLAND,
        ROCKY_SLOPE,
        ALPINE,
        COASTAL
    }

    private record Sample(
        int surfaceY,
        VegetationProfile profile,
        VegetationContext context,
        VegetationSelection selection
    ) {
    }
}
