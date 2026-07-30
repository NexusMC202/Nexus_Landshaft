package dev.nexusmc.landscape.worldgen.v2.surface;

import com.mojang.logging.LogUtils;
import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.NexusV2HydrologySampler;
import dev.nexusmc.landscape.worldgen.v2.terrain.AnalyticalTerrainMath;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;

/**
 * Applies Stage 6 profiles to the physical top/subsurface of one new chunk.
 * The pass only reads and writes the supplied chunk; analytical samples beyond
 * its edge are pure coordinate queries and never request neighbour chunks.
 */
public final class SurfaceProvincePass {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int UNKNOWN_LOG_LIMIT = 256;
    private static final Set<String> LOGGED_UNKNOWN = new LinkedHashSet<>();
    private static final Map<String, BlockState> MATERIALS = materials();

    private SurfaceProvincePass() {
    }

    public static void apply(
        WorldGenRegion level,
        ChunkAccess chunk,
        RandomState randomState
    ) {
        long seed = level.getSeed();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        NexusV2FieldSampler fields = new NexusV2FieldSampler(randomState);
        NexusV2HydrologySampler hydrology =
            new NexusV2HydrologySampler(randomState);
        NexusV2FieldSampler.SurfaceInputs[][] inputs =
            new NexusV2FieldSampler.SurfaceInputs[18][18];
        double[][] analyticalY = new double[18][18];

        for (int gridZ = 0; gridZ < 18; gridZ++) {
            for (int gridX = 0; gridX < 18; gridX++) {
                int worldX = minX + gridX - 1;
                int worldZ = minZ + gridZ - 1;
                NexusV2FieldSampler.SurfaceInputs input =
                    fields.surfaceInputs(worldX, worldZ);
                inputs[gridZ][gridX] = input;
                analyticalY[gridZ][gridX] = analytical(input).surfaceY();
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = minX + localX;
                int surfaceY = materialSurfaceY(chunk, worldX, worldZ);
                if (surfaceY <= chunk.getMinBuildHeight() + 4) {
                    continue;
                }
                cursor.set(worldX, surfaceY, worldZ);
                Holder<Biome> biome = level.getBiome(cursor);
                String biomeKey = biome.unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("nexus_landscape:unknown");

                NexusV2FieldSampler.SurfaceInputs input =
                    inputs[localZ + 1][localX + 1];
                RegionalFieldMath.Sample regional = regional(input);
                HydrologyMath.Sample river =
                    hydrology.sample(worldX, worldZ);
                HydrologyMath.BasinSample basin =
                    hydrology.basinSample(worldX, worldZ);
                double slope = slope(
                    analyticalY,
                    localX + 1,
                    localZ + 1
                );
                double coast = channel(input, RegionalFieldMath.Channel.COAST_WEIGHT);
                double volcanic = Math.max(
                    regional.provinceWeight(RegionalFieldMath.Province.VOLCANIC_BELT),
                    channel(input, RegionalFieldMath.Channel.VOLCANIC_MOUNTAINS)
                );
                double glacier = channel(
                    input,
                    RegionalFieldMath.Channel.GLACIER_MASS
                );
                double canyon = channel(
                    input,
                    RegionalFieldMath.Channel.CANYON_INCISION
                );
                double karst = regional.provinceWeight(
                    RegionalFieldMath.Province.KARST_BELT
                );
                double mycelial = regional.provinceWeight(
                    RegionalFieldMath.Province.MYCELIAL_CRATON
                );
                double archipelago = clamp01(
                    regional.provinceWeight(
                        RegionalFieldMath.Province.OCEANIC_CRUST
                    ) * (0.35 + coast * 0.65)
                );
                double groundwater = clamp01(
                    (input.humidity() + 1.0) * 0.34
                        + regional.provinceWeight(
                            RegionalFieldMath.Province.WETLAND_BASIN
                        ) * 0.48
                        + basin.mask() * 0.25
                );
                double alpine = clamp01(
                    (surfaceY - 128.0) / 96.0
                        + glacier * 0.55
                );
                SurfaceContext context = new SurfaceContext(
                    seed,
                    worldX,
                    worldZ,
                    surfaceY,
                    biomeKey,
                    regional.dominantProvince().serializedName(),
                    input.temperature(),
                    input.humidity(),
                    input.continentalness(),
                    input.erosion(),
                    input.weirdness(),
                    surfaceY,
                    clamp01((surfaceY + 64.0) / 384.0),
                    slope,
                    river.distance(),
                    river.mask(),
                    clamp01(river.mask() * 0.72
                        + smoothstep(42.0, 4.0, river.distance()) * 0.28),
                    basin.mask(),
                    coast,
                    192.0 * (1.0 - coast),
                    groundwater,
                    volcanic,
                    glacier,
                    alpine,
                    canyon,
                    karst,
                    mycelial,
                    archipelago,
                    SurfaceNoise.value(seed, worldX, worldZ, 48, 0x51FACEL),
                    SurfaceNoise.value(seed, worldX, worldZ, 11, 0x6D47E21L)
                );
                SurfaceProfile profile = SurfaceProfileCatalog.find(biomeKey)
                    .orElseGet(() -> fallback(
                        biomeKey,
                        input.temperature(),
                        input.humidity(),
                        surfaceY < 40
                    ));
                applyColumn(
                    chunk,
                    cursor,
                    profile,
                    SurfaceProfileResolver.resolve(profile, context),
                    context
                );
            }
        }
    }

    private static void applyColumn(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        SurfaceProfile profile,
        SurfaceSelection selection,
        SurfaceContext context
    ) {
        BlockState top = material(
            selection.topPalette(),
            context,
            0x70A11L
        );
        BlockState substrate = material(
            selection.substratePalette(),
            context,
            0x5AB501L
        );
        for (int layer = 0; layer < selection.depth(); layer++) {
            int y = context.surfaceY() - layer;
            cursor.set(context.blockX(), y, context.blockZ());
            BlockState existing = chunk.getBlockState(cursor);
            if (!replaceable(existing)) {
                if (layer == 0) {
                    return;
                }
                break;
            }
            chunk.setBlockState(
                cursor,
                layer == 0 ? top : substrate,
                false
            );
        }
    }

    private static int materialSurfaceY(
        ChunkAccess chunk,
        int worldX,
        int worldZ
    ) {
        int worldSurface = chunk.getHeight(
            Heightmap.Types.WORLD_SURFACE_WG,
            worldX,
            worldZ
        ) - 1;
        BlockPos pos = new BlockPos(worldX, worldSurface, worldZ);
        if (!chunk.getFluidState(pos).isEmpty()) {
            return chunk.getHeight(
                Heightmap.Types.OCEAN_FLOOR_WG,
                worldX,
                worldZ
            ) - 1;
        }
        return worldSurface;
    }

    private static BlockState material(
        List<String> palette,
        SurfaceContext context,
        long salt
    ) {
        long hash = SurfaceNoise.hash(
            context.worldSeed(),
            context.blockX(),
            context.blockZ(),
            salt ^ Double.doubleToLongBits(context.materialNoise())
        );
        String id = palette.get(Math.floorMod(hash, palette.size()));
        return MATERIALS.getOrDefault(id, Blocks.STONE.defaultBlockState());
    }

    private static boolean replaceable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.DIRT)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.ROOTED_DIRT)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.MYCELIUM)
            || state.is(Blocks.MUD)
            || state.is(Blocks.CLAY)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND)
            || state.is(Blocks.SANDSTONE)
            || state.is(Blocks.RED_SANDSTONE)
            || state.is(Blocks.STONE)
            || state.is(Blocks.ANDESITE)
            || state.is(Blocks.DIORITE)
            || state.is(Blocks.GRANITE)
            || state.is(Blocks.TUFF)
            || state.is(Blocks.CALCITE)
            || state.is(Blocks.DRIPSTONE_BLOCK)
            || state.is(Blocks.TERRACOTTA)
            || state.is(Blocks.WHITE_TERRACOTTA)
            || state.is(Blocks.ORANGE_TERRACOTTA)
            || state.is(Blocks.YELLOW_TERRACOTTA)
            || state.is(Blocks.RED_TERRACOTTA)
            || state.is(Blocks.SNOW_BLOCK)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.ICE)
            || state.is(Blocks.PACKED_ICE)
            || state.is(Blocks.BLUE_ICE)
            || state.is(Blocks.BASALT)
            || state.is(Blocks.SMOOTH_BASALT)
            || state.is(Blocks.BLACKSTONE)
            || state.is(Blocks.MOSS_BLOCK);
    }

    private static SurfaceProfile fallback(
        String biomeKey,
        double temperature,
        double humidity,
        boolean underground
    ) {
        logUnknown(biomeKey);
        return SurfaceProfileCatalog.fallback(
            temperature,
            humidity,
            underground
        );
    }

    private static void logUnknown(String biomeKey) {
        synchronized (LOGGED_UNKNOWN) {
            if (LOGGED_UNKNOWN.size() >= UNKNOWN_LOG_LIMIT
                || !LOGGED_UNKNOWN.add(biomeKey)) {
                return;
            }
        }
        LOGGER.info(
            "Nexus Stage 6: using climate-aware surface fallback for biome {}",
            biomeKey
        );
    }

    private static RegionalFieldMath.Sample regional(
        NexusV2FieldSampler.SurfaceInputs input
    ) {
        return RegionalFieldMath.sample(
            input.continentalness(),
            input.temperature(),
            input.humidity(),
            input.macro(),
            input.detail(),
            input.ridge(),
            input.volcanic(),
            input.composition()
        );
    }

    private static double channel(
        NexusV2FieldSampler.SurfaceInputs input,
        RegionalFieldMath.Channel channel
    ) {
        return RegionalFieldMath.compute(
            channel,
            input.continentalness(),
            input.temperature(),
            input.humidity(),
            input.macro(),
            input.detail(),
            input.ridge(),
            input.volcanic(),
            input.composition()
        );
    }

    private static AnalyticalTerrainMath.Sample analytical(
        NexusV2FieldSampler.SurfaceInputs input
    ) {
        return AnalyticalTerrainMath.sample(
            input.continentalness(),
            input.temperature(),
            input.humidity(),
            input.macro(),
            input.detail(),
            input.ridge(),
            input.volcanic(),
            input.composition()
        );
    }

    private static double slope(double[][] heights, int x, int z) {
        double dx = heights[z][x + 1] - heights[z][x - 1];
        double dz = heights[z + 1][x] - heights[z - 1][x];
        return clamp01(Math.sqrt(dx * dx + dz * dz) / 16.0);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 < edge0) {
            return 1.0 - smoothstep(edge1, edge0, value);
        }
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Map<String, BlockState> materials() {
        Map<String, BlockState> result = new HashMap<>();
        for (SurfaceProfile profile : SurfaceProfileCatalog.profiles().values()) {
            SurfaceProfile.Layers layers = profile.layers();
            add(result, layers.top());
            add(result, layers.soil());
            add(result, layers.transition());
            add(result, layers.exposedRock());
            add(result, layers.wet());
            add(result, layers.sediment());
            add(result, layers.coast());
            add(result, layers.alpine());
        }
        return Map.copyOf(result);
    }

    private static void add(Map<String, BlockState> result, List<String> ids) {
        for (String id : ids) {
            BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
                .ifPresent(block -> result.put(id, block.defaultBlockState()));
        }
    }
}
