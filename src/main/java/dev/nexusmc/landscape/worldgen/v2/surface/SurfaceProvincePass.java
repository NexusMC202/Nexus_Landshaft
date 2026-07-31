package dev.nexusmc.landscape.worldgen.v2.surface;

import com.mojang.logging.LogUtils;
import dev.nexusmc.landscape.diagnostics.Stage6Profiler;
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
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
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
    private static final Map<RandomState, Telemetry> TELEMETRY =
        Collections.synchronizedMap(new WeakHashMap<>());

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
        HydrologyChunkGrid hydrologyGrid =
            new HydrologyChunkGrid(minX, minZ, hydrology);
        Telemetry telemetry = telemetry(randomState);
        NexusV2FieldSampler.SurfaceInputs[][] inputs =
            new NexusV2FieldSampler.SurfaceInputs[18][18];
        double[][] analyticalY = new double[18][18];

        long phaseStarted = Stage6Profiler.start();
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
        Stage6Profiler.record(
            Stage6Profiler.Phase.SURFACE_NOISE_SAMPLING,
            phaseStarted
        );

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
                phaseStarted = Stage6Profiler.start();
                Holder<Biome> biome = level.getBiome(cursor);
                String biomeKey = biome.unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("nexus_landscape:unknown");
                Stage6Profiler.record(
                    Stage6Profiler.Phase.SURFACE_BIOME_LOOKUP,
                    phaseStarted
                );

                phaseStarted = Stage6Profiler.start();
                NexusV2FieldSampler.SurfaceInputs input =
                    inputs[localZ + 1][localX + 1];
                RegionalFieldMath.Sample regional =
                    SurfaceContextFactory.regional(input);
                HydrologyMath.Sample river =
                    hydrologyGrid.river(worldX, worldZ);
                HydrologyMath.BasinSample basin =
                    hydrologyGrid.basin(worldX, worldZ);
                double slope = slope(
                    analyticalY,
                    localX + 1,
                    localZ + 1
                );
                SurfaceContext context = SurfaceContextFactory.create(
                    seed,
                    worldX,
                    worldZ,
                    surfaceY,
                    biomeKey,
                    input,
                    regional,
                    river,
                    basin,
                    slope
                );
                SurfaceProfile profile = SurfaceProfileCatalog.find(biomeKey)
                    .filter(candidate -> candidate.elevation()
                        != SurfaceProfile.ElevationBand.SUBTERRANEAN)
                    .orElseGet(() -> fallback(
                        biomeKey,
                        input.temperature(),
                        input.humidity(),
                        surfaceY < 40
                    ));
                SurfaceSelection selection =
                    SurfaceProfileResolver.resolve(profile, context);
                Stage6Profiler.record(
                    Stage6Profiler.Phase.SURFACE_CONTEXT_PREPARATION,
                    phaseStarted
                );
                phaseStarted = Stage6Profiler.start();
                int changed = applyColumn(
                    chunk,
                    cursor,
                    profile,
                    selection,
                    context
                );
                Stage6Profiler.record(
                    Stage6Profiler.Phase.SURFACE_BLOCK_REPLACEMENT,
                    phaseStarted
                );
                telemetry.columns.increment();
                telemetry.blocks.add(changed);
                telemetry.zones[selection.zone().ordinal()].increment();
                validateDominantZone(context, selection, telemetry);
            }
        }
    }

    public static String snapshotAndReset(RandomState randomState) {
        Telemetry telemetry = telemetry(randomState);
        StringBuilder result = new StringBuilder();
        result.append("surface.columns=").append(telemetry.columns.sumThenReset())
            .append('\n');
        result.append("surface.blocks_changed=")
            .append(telemetry.blocks.sumThenReset()).append('\n');
        for (SurfaceSelection.Zone zone : SurfaceSelection.Zone.values()) {
            result.append("surface.zone.")
                .append(zone.name().toLowerCase(java.util.Locale.ROOT))
                .append('=')
                .append(telemetry.zones[zone.ordinal()].sumThenReset())
                .append('\n');
        }
        result.append("surface.zone.river=")
            .append(telemetry.aliasRiver.sumThenReset()).append('\n');
        result.append("surface.zone.lake=")
            .append(telemetry.aliasLake.sumThenReset()).append('\n');
        result.append("surface.zone.slope=")
            .append(telemetry.aliasSlope.sumThenReset()).append('\n');
        result.append("surface.invalid.river_coast_dominance=")
            .append(telemetry.invalidRiverCoast.sumThenReset()).append('\n');
        result.append("surface.invalid.alpine_low_altitude=")
            .append(telemetry.invalidAlpineLow.sumThenReset()).append('\n');
        result.append("surface.invalid.wet_bank_far_from_water=")
            .append(telemetry.invalidWetBankFar.sumThenReset()).append('\n');
        result.append("surface.invalid.lake_shore_on_channel=")
            .append(telemetry.invalidLakeOnChannel.sumThenReset()).append('\n');
        result.append("surface.invalid.processing_below_minimum=")
            .append(telemetry.invalidBelowMinimum.sumThenReset()).append('\n');
        return result.toString();
    }

    private static void validateDominantZone(
        SurfaceContext context,
        SurfaceSelection selection,
        Telemetry telemetry
    ) {
        switch (selection.zone()) {
            case CHANNEL -> telemetry.aliasRiver.increment();
            case LAKE_SHORE -> telemetry.aliasLake.increment();
            case EXPOSED_SLOPE -> telemetry.aliasSlope.increment();
            default -> {
            }
        }
        if (selection.zone() == SurfaceSelection.Zone.COAST
            && context.activeChannel()) {
            telemetry.invalidRiverCoast.increment();
        }
        if (selection.zone() == SurfaceSelection.Zone.ALPINE
            && context.surfaceY() < 96
            && context.glacierWeight() < 0.55) {
            telemetry.invalidAlpineLow.increment();
        }
        if (selection.zone() == SurfaceSelection.Zone.WET_BANK
            && context.riverDistance() > 24.0
            && context.groundwater() < 0.65
            && context.lakeBasinMask() < 0.10) {
            telemetry.invalidWetBankFar.increment();
        }
        if (selection.zone() == SurfaceSelection.Zone.LAKE_SHORE
            && context.activeChannel()) {
            telemetry.invalidLakeOnChannel.increment();
        }
        if (context.surfaceY() < -60) {
            telemetry.invalidBelowMinimum.increment();
        }
    }

    private static int applyColumn(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        SurfaceProfile profile,
        SurfaceSelection selection,
        SurfaceContext context
    ) {
        int changed = 0;
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
                    return 0;
                }
                break;
            }
            chunk.setBlockState(
                cursor,
                layer == 0 ? top : substrate,
                false
            );
            changed++;
        }
        return changed;
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

    private static Telemetry telemetry(RandomState randomState) {
        synchronized (TELEMETRY) {
            return TELEMETRY.computeIfAbsent(
                randomState,
                ignored -> new Telemetry()
            );
        }
    }

    private static final class Telemetry {
        private final LongAdder columns = new LongAdder();
        private final LongAdder blocks = new LongAdder();
        private final LongAdder[] zones =
            new LongAdder[SurfaceSelection.Zone.values().length];
        private final LongAdder aliasRiver = new LongAdder();
        private final LongAdder aliasLake = new LongAdder();
        private final LongAdder aliasSlope = new LongAdder();
        private final LongAdder invalidRiverCoast = new LongAdder();
        private final LongAdder invalidAlpineLow = new LongAdder();
        private final LongAdder invalidWetBankFar = new LongAdder();
        private final LongAdder invalidLakeOnChannel = new LongAdder();
        private final LongAdder invalidBelowMinimum = new LongAdder();

        private Telemetry() {
            for (int index = 0; index < zones.length; index++) {
                zones[index] = new LongAdder();
            }
        }
    }
}
