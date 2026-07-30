package dev.nexusmc.landscape.worldgen.v2.vegetation;

import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.NexusV2HydrologySampler;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContext;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContextFactory;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceNoise;
import java.util.HashMap;
import java.util.Map;
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
                    worldZ
                );
            }
        }
        placeOwnedTrees(level, chunk, fields, hydrology, seed);
        decorateCaves(level, chunk, seed);
    }

    private static void decorateGround(
        WorldGenLevel level,
        NexusV2FieldSampler fields,
        NexusV2HydrologySampler hydrology,
        long seed,
        int x,
        int z
    ) {
        Sample sample = sample(level, fields, hydrology, seed, x, z);
        if (sample == null || !sample.selection().terrestrialAllowed()) {
            return;
        }
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
        }
    }

    private static void placeOwnedTrees(
        WorldGenLevel level,
        ChunkAccess chunk,
        NexusV2FieldSampler fields,
        NexusV2HydrologySampler hydrology,
        long seed
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
                Sample sample = sample(level, fields, hydrology, seed, x, z);
                if (sample == null
                    || !sample.selection().treesAllowed()
                    || unit(seed, x, z, 0x7AEE1L)
                        >= sample.selection().treeDensity()) {
                    continue;
                }
                VegetationProfile.TreeShape shape = sample.profile().treeShapes()
                    .get(Math.floorMod(
                        SurfaceNoise.hash(seed, x, z, 0x5A9EL),
                        sample.profile().treeShapes().size()
                    ));
                buildTree(
                    level,
                    new BlockPos(x, sample.surfaceY() + 1, z),
                    shape,
                    unit(seed, x, z, 0x01D6L)
                        < sample.selection().oldGrowthDensity()
                );
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
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surfaceY <= level.getMinBuildHeight() + 4) {
            return null;
        }
        BlockPos surface = new BlockPos(x, surfaceY, z);
        Holder<Biome> biome = level.getBiome(surface);
        String biomeId = biome.unwrapKey()
            .map(key -> key.location().toString())
            .orElse("nexus_landscape:unknown");
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
        return new Sample(
            surfaceY,
            profile,
            context,
            VegetationResolver.resolve(profile, context)
        );
    }

    private static void decorateCaves(
        WorldGenLevel level,
        ChunkAccess chunk,
        long seed
    ) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localZ : new int[] {3, 11}) {
            for (int localX : new int[] {3, 11}) {
                int x = minX + localX;
                int z = minZ + localZ;
                for (int y : new int[] {-32, 0, 32}) {
                    cursor.set(x, y, z);
                    String biomeId = level.getBiome(cursor).unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("");
                    VegetationProfile profile =
                        VegetationProfileCatalog.find(biomeId).orElse(null);
                    if (profile == null || profile.terrestrial()) {
                        continue;
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
                    }
                }
            }
        }
    }

    private static void buildTree(
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
            return;
        }
        for (int y = 0; y < height; y++) {
            BlockPos position = base.above(y);
            if (!level.getBlockState(position).canBeReplaced()) {
                return;
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

    private record Sample(
        int surfaceY,
        VegetationProfile profile,
        VegetationContext context,
        VegetationSelection selection
    ) {
    }
}
