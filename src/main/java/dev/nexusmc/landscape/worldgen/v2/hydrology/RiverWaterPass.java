package dev.nexusmc.landscape.worldgen.v2.hydrology;

import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Chunk-local finalization of analytically confirmed river profiles.
 *
 * <p>The coarse grid is only a rejection accelerator. Every accepted column is
 * resampled against its nearest canonical segment, so stream order never leaks
 * from a high-order corner through bilinear interpolation.
 */
public final class RiverWaterPass {
    private static final Map<RandomState, Telemetry> RUN_TELEMETRY =
        Collections.synchronizedMap(new WeakHashMap<>());

    private RiverWaterPass() {
    }

    public static void apply(ChunkAccess chunk, RandomState randomState) {
        Telemetry telemetry = telemetry(randomState);
        NexusV2HydrologySampler hydrology =
            new NexusV2HydrologySampler(randomState);
        NexusV2FieldSampler fields = new NexusV2FieldSampler(randomState);
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] grid = {0, 4, 8, 12, 15};
        HydrologyMath.Sample[][] samples = new HydrologyMath.Sample[5][5];
        for (int gridZ = 0; gridZ < grid.length; gridZ++) {
            for (int gridX = 0; gridX < grid.length; gridX++) {
                samples[gridZ][gridX] = hydrology.sample(
                    minX + grid[gridX],
                    minZ + grid[gridZ]
                );
            }
        }

        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = minX + localX;
                if (interpolatedMask(samples, grid, localX, localZ) < 0.18) {
                    continue;
                }

                HydrologyMath.Sample river = hydrology.sample(worldX, worldZ);
                if (river.canonicalSegmentId() == HydrologyMath.NO_NODE) {
                    continue;
                }
                double halfWidth = 17.0 + river.order() * 7.0;
                double normalizedDistance = river.distance() / halfWidth;
                ProfileZone zone = ProfileZone.at(normalizedDistance);
                if (zone == null) {
                    continue;
                }
                telemetry.channelsAttempted.increment();

                RegionalFieldMath.Sample regional = fields.sample(worldX, worldZ);
                double slope = analyticalSlope(fields, worldX, worldZ);
                double floodplainWeight = floodplainWeight(regional, slope);
                RiverFamily family = RiverFamily.select(
                    regional.dominantProvince(),
                    river.order(),
                    river.accumulation(),
                    slope,
                    floodplainWeight
                );
                CrossSection profile = CrossSection.create(
                    zone,
                    normalizedDistance,
                    river.order(),
                    slope,
                    floodplainWeight,
                    family
                );

                int surfaceY = chunk.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    worldX,
                    worldZ
                ) - 1;
                int bedY = profile.wetted()
                    ? Math.max(
                        (int)Math.floor(river.bedY()),
                        (int)Math.floor(surfaceY - profile.depth())
                    )
                    : surfaceY - (int)Math.floor(profile.depth());
                int waterY = Math.min(
                    (int)Math.floor(river.waterY()),
                    surfaceY - 1
                );

                if (profile.wetted()) {
                    double incision = surfaceY - bedY;
                    if (incision < 1.0 || waterY <= bedY) {
                        telemetry.terrainMismatch.increment();
                        telemetry.bedAboveSurface.increment();
                        continue;
                    }
                    if (incision > profile.maximumIncision()) {
                        telemetry.terrainMismatch.increment();
                        telemetry.bedTooDeep.increment();
                        continue;
                    }
                }
                if (bedY < minY + 5 || surfaceY > maxY - 2) {
                    telemetry.outOfBounds.increment();
                    continue;
                }

                CaveSupport support = validateCaveSupport(
                    chunk,
                    cursor,
                    worldX,
                    worldZ,
                    bedY,
                    surfaceY,
                    river,
                    zone
                );
                telemetry.record(support);
                if (!support.allowed()) {
                    telemetry.caveIntersection.increment();
                    continue;
                }

                applyProfile(
                    chunk,
                    cursor,
                    worldX,
                    worldZ,
                    surfaceY,
                    bedY,
                    waterY,
                    profile,
                    family,
                    regional.dominantProvince(),
                    river.order(),
                    telemetry
                );
                telemetry.channelsAccepted.increment();
            }
        }
        applyBasins(
            chunk,
            hydrology,
            telemetry,
            cursor,
            minX,
            minZ,
            minY,
            maxY
        );
    }

    private static void applyBasins(
        ChunkAccess chunk,
        NexusV2HydrologySampler hydrology,
        Telemetry telemetry,
        BlockPos.MutableBlockPos cursor,
        int minX,
        int minZ,
        int minY,
        int maxY
    ) {
        int[] basinGrid = {0, 4, 8, 12, 15};
        HydrologyMath.BasinSample[][] basinSamples =
            new HydrologyMath.BasinSample[5][5];
        for (int gridZ = 0; gridZ < basinGrid.length; gridZ++) {
            for (int gridX = 0; gridX < basinGrid.length; gridX++) {
                basinSamples[gridZ][gridX] = hydrology.basinSample(
                    minX + basinGrid[gridX],
                    minZ + basinGrid[gridZ]
                );
            }
        }
        for (int localZ = 0; localZ < 16; localZ++) {
            int z = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int x = minX + localX;
                HydrologyMath.BasinSample basin = interpolateBasin(
                    basinSamples,
                    basinGrid,
                    localX,
                    localZ
                );
                if (basin.reason() == HydrologyMath.TerminalReason.NONE
                    || basin.mask() <= 0.02) {
                    continue;
                }
                telemetry.basinColumnsAttempted.increment();
                int surfaceY = chunk.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    x,
                    z
                ) - 1;
                int bedY = (int)Math.floor(basin.bedY());
                int waterY = (int)Math.floor(basin.waterY());
                if (bedY < minY + 5 || waterY > maxY - 2) {
                    telemetry.outOfBounds.increment();
                    continue;
                }

                boolean lakeInterior =
                    basin.reason() == HydrologyMath.TerminalReason.LAKE
                    && basin.radialDistance() <= 1.0;
                boolean overflow =
                    basin.reason()
                        == HydrologyMath.TerminalReason.DETERMINISTIC_OVERFLOW_OUTLET
                    && basin.mask() >= 0.52;
                if (lakeInterior || overflow) {
                    bedY = Math.max(bedY, surfaceY - (lakeInterior ? 12 : 7));
                    waterY = Math.max(bedY + 1, Math.min(waterY, surfaceY + 2));
                    if (!basinSupportIsSafe(chunk, cursor, x, z, bedY)) {
                        telemetry.caveIntersection.increment();
                        continue;
                    }
                    for (int y = surfaceY; y > waterY; y--) {
                        cursor.set(x, y, z);
                        if (!chunk.getBlockState(cursor).isAir()) {
                            chunk.setBlockState(
                                cursor,
                                Blocks.AIR.defaultBlockState(),
                                false
                            );
                            telemetry.blocksCarved.increment();
                        }
                    }
                    for (int y = bedY + 1; y <= waterY; y++) {
                        cursor.set(x, y, z);
                        chunk.setBlockState(
                            cursor,
                            Blocks.WATER.defaultBlockState(),
                            false
                        );
                        telemetry.waterBlocks.increment();
                        telemetry.basinWaterBlocks.increment();
                    }
                    cursor.set(x, bedY, z);
                    chunk.setBlockState(
                        cursor,
                        overflow
                            ? Blocks.GRAVEL.defaultBlockState()
                            : Blocks.CLAY.defaultBlockState(),
                        false
                    );
                    telemetry.sedimentBlocks.increment();
                    if (overflow) {
                        telemetry.overflowColumnsCarved.increment();
                    } else {
                        telemetry.lakeColumnsCarved.increment();
                    }
                } else if (basin.shorelineWeight() > 0.08) {
                    cursor.set(x, surfaceY, z);
                    chunk.setBlockState(
                        cursor,
                        basin.closedBasin()
                            ? Blocks.CLAY.defaultBlockState()
                            : Blocks.SAND.defaultBlockState(),
                        false
                    );
                    telemetry.sedimentBlocks.increment();
                    telemetry.shorelineColumns.increment();
                }
            }
        }
    }

    private static HydrologyMath.BasinSample interpolateBasin(
        HydrologyMath.BasinSample[][] samples,
        int[] grid,
        int localX,
        int localZ
    ) {
        int x0 = interval(grid, localX);
        int z0 = interval(grid, localZ);
        int x1 = Math.min(x0 + 1, grid.length - 1);
        int z1 = Math.min(z0 + 1, grid.length - 1);
        double tx = grid[x1] == grid[x0]
            ? 0.0
            : (localX - grid[x0]) / (double)(grid[x1] - grid[x0]);
        double tz = grid[z1] == grid[z0]
            ? 0.0
            : (localZ - grid[z0]) / (double)(grid[z1] - grid[z0]);
        HydrologyMath.BasinSample[] corners = {
            samples[z0][x0],
            samples[z0][x1],
            samples[z1][x0],
            samples[z1][x1]
        };
        HydrologyMath.BasinSample dominant = corners[0];
        for (HydrologyMath.BasinSample corner : corners) {
            if (corner.mask() > dominant.mask()) {
                dominant = corner;
            }
        }
        if (dominant.reason() == HydrologyMath.TerminalReason.NONE) {
            return dominant;
        }
        double[] weights = {
            (1.0 - tx) * (1.0 - tz),
            tx * (1.0 - tz),
            (1.0 - tx) * tz,
            tx * tz
        };
        double weight = 0.0;
        double mask = 0.0;
        double radial = 0.0;
        double shore = 0.0;
        double bed = 0.0;
        double water = 0.0;
        for (int index = 0; index < corners.length; index++) {
            HydrologyMath.BasinSample corner = corners[index];
            if (corner.basinId() != dominant.basinId()
                || corner.reason() != dominant.reason()) {
                continue;
            }
            double cornerWeight = weights[index];
            weight += cornerWeight;
            mask += corner.mask() * cornerWeight;
            radial += corner.radialDistance() * cornerWeight;
            shore += corner.shorelineWeight() * cornerWeight;
            bed += corner.bedY() * cornerWeight;
            water += corner.waterY() * cornerWeight;
        }
        if (weight <= 1.0E-9) {
            return dominant;
        }
        return new HydrologyMath.BasinSample(
            dominant.reason(),
            dominant.basinId(),
            dominant.outletId(),
            radial / weight,
            mask / weight,
            shore / weight,
            bed / weight,
            water / weight,
            dominant.closedBasin()
        );
    }

    private static boolean basinSupportIsSafe(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int x,
        int z,
        int bedY
    ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int supportX = x + dx;
                int supportZ = z + dz;
                if (!inside(chunk, supportX, supportZ)) {
                    continue;
                }
                for (int depth = 1; depth <= 4; depth++) {
                    cursor.set(supportX, bedY - depth, supportZ);
                    if (chunk.getBlockState(cursor).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void applyProfile(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int x,
        int z,
        int surfaceY,
        int bedY,
        int waterY,
        CrossSection profile,
        RiverFamily family,
        RegionalFieldMath.Province province,
        int order,
        Telemetry telemetry
    ) {
        SedimentPalette palette = SedimentPalette.select(family, province, order);
        if (profile.wetted()) {
            for (int y = surfaceY; y > waterY; y--) {
                cursor.set(x, y, z);
                if (!chunk.getBlockState(cursor).isAir()) {
                    chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
                    telemetry.blocksCarved.increment();
                }
            }
            for (int y = bedY + 1; y <= waterY; y++) {
                cursor.set(x, y, z);
                chunk.setBlockState(cursor, Blocks.WATER.defaultBlockState(), false);
                telemetry.waterBlocks.increment();
            }
        } else if (bedY < surfaceY) {
            for (int y = surfaceY; y > bedY; y--) {
                cursor.set(x, y, z);
                if (!chunk.getBlockState(cursor).isAir()) {
                    chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
                    telemetry.blocksCarved.increment();
                }
            }
        }

        int layers = profile.sedimentLayers();
        for (int layer = 0; layer < layers; layer++) {
            int y = bedY - layer;
            cursor.set(x, y, z);
            chunk.setBlockState(
                cursor,
                layer == 0 ? palette.surface() : palette.substrate(),
                false
            );
            telemetry.sedimentBlocks.increment();
        }
    }

    private static CaveSupport validateCaveSupport(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int x,
        int z,
        int bedY,
        int surfaceY,
        HydrologyMath.Sample river,
        ProfileZone zone
    ) {
        int supportRadius = Math.min(3, 1 + river.order());
        int air = 0;
        int fluid = 0;
        int thinColumns = 0;
        int entranceColumns = 0;
        double normalX = -river.flowZ();
        double normalZ = river.flowX();
        for (int offset = -supportRadius; offset <= supportRadius; offset++) {
            int supportX = x + (int)Math.round(normalX * offset);
            int supportZ = z + (int)Math.round(normalZ * offset);
            if (!inside(chunk, supportX, supportZ)) {
                continue;
            }
            int firstVoidDepth = Integer.MAX_VALUE;
            for (int depth = 1; depth <= 6; depth++) {
                cursor.set(supportX, bedY - depth, supportZ);
                if (!chunk.getFluidState(cursor).isEmpty()) {
                    fluid++;
                    firstVoidDepth = Math.min(firstVoidDepth, depth);
                } else if (chunk.getBlockState(cursor).isAir()) {
                    air++;
                    firstVoidDepth = Math.min(firstVoidDepth, depth);
                }
            }
            if (firstVoidDepth <= 2) {
                thinColumns++;
            }
            for (int y = bedY; y <= Math.min(surfaceY, bedY + 3); y++) {
                cursor.set(supportX, y, supportZ);
                if (chunk.getBlockState(cursor).isAir()) {
                    entranceColumns++;
                    break;
                }
            }
        }
        if (air == 0 && fluid == 0) {
            return CaveSupport.SAFE_GROUND;
        }
        if (fluid > 0 && air == 0) {
            return CaveSupport.AQUIFER;
        }
        if (entranceColumns > 0 && zone.wetted()) {
            return CaveSupport.ALLOWED_RIVER_CAVE_CONNECTION;
        }
        if (thinColumns > 0) {
            return CaveSupport.THIN_ROOF;
        }
        if (entranceColumns > 0) {
            return CaveSupport.CAVE_ENTRANCE;
        }
        return CaveSupport.RANDOM_BREAKTHROUGH;
    }

    private static boolean inside(ChunkAccess chunk, int x, int z) {
        return x >= chunk.getPos().getMinBlockX()
            && x <= chunk.getPos().getMaxBlockX()
            && z >= chunk.getPos().getMinBlockZ()
            && z <= chunk.getPos().getMaxBlockZ();
    }

    private static double analyticalSlope(
        NexusV2FieldSampler fields,
        int x,
        int z
    ) {
        double dx = fields.analyticalTerrain(x + 4, z).surfaceY()
            - fields.analyticalTerrain(x - 4, z).surfaceY();
        double dz = fields.analyticalTerrain(x, z + 4).surfaceY()
            - fields.analyticalTerrain(x, z - 4).surfaceY();
        return Math.min(1.0, Math.hypot(dx, dz) / 8.0);
    }

    private static double floodplainWeight(
        RegionalFieldMath.Sample regional,
        double slope
    ) {
        double basin = regional.provinceWeight(
            RegionalFieldMath.Province.WETLAND_BASIN
        );
        double lowland = regional.provinceWeight(
            RegionalFieldMath.Province.SEDIMENTARY_LOWLAND
        );
        return clamp01((basin * 0.75 + lowland * 0.55) * (1.0 - slope));
    }

    private static double interpolatedMask(
        HydrologyMath.Sample[][] samples,
        int[] grid,
        int localX,
        int localZ
    ) {
        int x0 = interval(grid, localX);
        int z0 = interval(grid, localZ);
        int x1 = Math.min(x0 + 1, grid.length - 1);
        int z1 = Math.min(z0 + 1, grid.length - 1);
        double tx = grid[x1] == grid[x0]
            ? 0.0
            : (localX - grid[x0]) / (double)(grid[x1] - grid[x0]);
        double tz = grid[z1] == grid[z0]
            ? 0.0
            : (localZ - grid[z0]) / (double)(grid[z1] - grid[z0]);
        return bilerp(
            samples[z0][x0].mask(),
            samples[z0][x1].mask(),
            samples[z1][x0].mask(),
            samples[z1][x1].mask(),
            tx,
            tz
        );
    }

    private static int interval(int[] grid, int value) {
        for (int index = 0; index < grid.length - 1; index++) {
            if (value <= grid[index + 1]) {
                return index;
            }
        }
        return grid.length - 1;
    }

    private static double bilerp(
        double a,
        double b,
        double c,
        double d,
        double tx,
        double tz
    ) {
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Telemetry telemetry(RandomState randomState) {
        synchronized (RUN_TELEMETRY) {
            return RUN_TELEMETRY.computeIfAbsent(
                randomState,
                ignored -> new Telemetry()
            );
        }
    }

    public static Counters snapshot(RandomState randomState) {
        return telemetry(randomState).snapshot();
    }

    public static Counters snapshotAndReset(RandomState randomState) {
        synchronized (RUN_TELEMETRY) {
            Telemetry removed = RUN_TELEMETRY.remove(randomState);
            return removed == null ? Counters.EMPTY : removed.snapshot();
        }
    }

    private enum ProfileZone {
        CHANNEL_CENTER(true),
        DEEP_BED(true),
        SHALLOW_SHELF(true),
        WET_BANK(false),
        DEPOSITIONAL_BANK(false),
        FLOODPLAIN_TRANSITION(false);

        private final boolean wetted;

        ProfileZone(boolean wetted) {
            this.wetted = wetted;
        }

        boolean wetted() {
            return wetted;
        }

        static ProfileZone at(double distance) {
            if (distance <= 0.18) return CHANNEL_CENTER;
            if (distance <= 0.42) return DEEP_BED;
            if (distance <= 0.68) return SHALLOW_SHELF;
            if (distance <= 0.90) return WET_BANK;
            if (distance <= 1.18) return DEPOSITIONAL_BANK;
            if (distance <= 1.55) return FLOODPLAIN_TRANSITION;
            return null;
        }
    }

    private enum RiverFamily {
        LOWLAND_MEANDERING,
        MOUNTAIN_TORRENT,
        CANYON,
        WETLAND_DISTRIBUTARY,
        VOLCANIC,
        TEMPERATE;

        static RiverFamily select(
            RegionalFieldMath.Province province,
            int order,
            long accumulation,
            double slope,
            double floodplain
        ) {
            if (province == RegionalFieldMath.Province.VOLCANIC_BELT) {
                return VOLCANIC;
            }
            if (province == RegionalFieldMath.Province.DRY_PLATEAU) {
                return CANYON;
            }
            if (floodplain > 0.42) {
                return province == RegionalFieldMath.Province.WETLAND_BASIN
                    ? WETLAND_DISTRIBUTARY
                    : LOWLAND_MEANDERING;
            }
            if (slope > 0.34 && (order <= 2 || accumulation < 12)) {
                return MOUNTAIN_TORRENT;
            }
            return TEMPERATE;
        }
    }

    private record CrossSection(
        ProfileZone zone,
        double depth,
        double maximumIncision,
        int sedimentLayers
    ) {
        boolean wetted() {
            return zone.wetted();
        }

        static CrossSection create(
            ProfileZone zone,
            double normalizedDistance,
            int order,
            double slope,
            double floodplain,
            RiverFamily family
        ) {
            double centerFactor = 1.0 - Math.min(1.0, normalizedDistance);
            double orderDepth = 1.4 + order * 1.05;
            double familyDepth = switch (family) {
                case MOUNTAIN_TORRENT, CANYON -> 1.25;
                case LOWLAND_MEANDERING, WETLAND_DISTRIBUTARY -> 0.82;
                default -> 1.0;
            };
            double depth = switch (zone) {
                case CHANNEL_CENTER ->
                    (orderDepth + 2.4 * centerFactor) * familyDepth;
                case DEEP_BED ->
                    (orderDepth + 1.1 * centerFactor) * familyDepth;
                case SHALLOW_SHELF ->
                    Math.max(1.25, orderDepth * 0.46);
                case WET_BANK -> 0.55 + floodplain * 0.65;
                case DEPOSITIONAL_BANK -> floodplain > 0.35 ? 0.35 : 0.0;
                case FLOODPLAIN_TRANSITION -> floodplain > 0.58 ? 0.2 : 0.0;
            };
            depth *= 1.0 + Math.min(0.35, slope * 0.28);
            int layers = switch (zone) {
                case CHANNEL_CENTER -> order >= 3 ? 3 : 2;
                case DEEP_BED -> 2;
                case SHALLOW_SHELF, DEPOSITIONAL_BANK -> 1;
                case WET_BANK -> floodplain > 0.3 ? 2 : 1;
                case FLOODPLAIN_TRANSITION -> floodplain > 0.58 ? 1 : 0;
            };
            return new CrossSection(zone, depth, 8.0 + order * 2.0, layers);
        }
    }

    private record SedimentPalette(BlockState surface, BlockState substrate) {
        static SedimentPalette select(
            RiverFamily family,
            RegionalFieldMath.Province province,
            int order
        ) {
            if (family == RiverFamily.VOLCANIC) {
                return new SedimentPalette(
                    Blocks.GRAVEL.defaultBlockState(),
                    Blocks.BASALT.defaultBlockState()
                );
            }
            if (family == RiverFamily.CANYON
                || province == RegionalFieldMath.Province.DRY_PLATEAU) {
                return new SedimentPalette(
                    Blocks.RED_SAND.defaultBlockState(),
                    Blocks.TERRACOTTA.defaultBlockState()
                );
            }
            if (family == RiverFamily.WETLAND_DISTRIBUTARY) {
                return new SedimentPalette(
                    Blocks.MUD.defaultBlockState(),
                    Blocks.CLAY.defaultBlockState()
                );
            }
            return new SedimentPalette(
                order >= 3
                    ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.SAND.defaultBlockState(),
                order >= 3
                    ? Blocks.CLAY.defaultBlockState()
                    : Blocks.DIRT.defaultBlockState()
            );
        }
    }

    private enum CaveSupport {
        SAFE_GROUND(true),
        THIN_ROOF(false),
        CAVE_ENTRANCE(false),
        AQUIFER(true),
        ALLOWED_RIVER_CAVE_CONNECTION(true),
        RANDOM_BREAKTHROUGH(false);

        private final boolean allowed;

        CaveSupport(boolean allowed) {
            this.allowed = allowed;
        }

        boolean allowed() {
            return allowed;
        }
    }

    private static final class Telemetry {
        private final LongAdder channelsAttempted = new LongAdder();
        private final LongAdder channelsAccepted = new LongAdder();
        private final LongAdder blocksCarved = new LongAdder();
        private final LongAdder waterBlocks = new LongAdder();
        private final LongAdder sedimentBlocks = new LongAdder();
        private final LongAdder terrainMismatch = new LongAdder();
        private final LongAdder bedAboveSurface = new LongAdder();
        private final LongAdder bedTooDeep = new LongAdder();
        private final LongAdder caveIntersection = new LongAdder();
        private final LongAdder outOfBounds = new LongAdder();
        private final LongAdder safeGround = new LongAdder();
        private final LongAdder thinRoof = new LongAdder();
        private final LongAdder caveEntrance = new LongAdder();
        private final LongAdder aquifer = new LongAdder();
        private final LongAdder allowedConnection = new LongAdder();
        private final LongAdder randomBreakthrough = new LongAdder();
        private final LongAdder basinColumnsAttempted = new LongAdder();
        private final LongAdder lakeColumnsCarved = new LongAdder();
        private final LongAdder basinWaterBlocks = new LongAdder();
        private final LongAdder shorelineColumns = new LongAdder();
        private final LongAdder overflowColumnsCarved = new LongAdder();

        void record(CaveSupport support) {
            switch (support) {
                case SAFE_GROUND -> safeGround.increment();
                case THIN_ROOF -> thinRoof.increment();
                case CAVE_ENTRANCE -> caveEntrance.increment();
                case AQUIFER -> aquifer.increment();
                case ALLOWED_RIVER_CAVE_CONNECTION -> allowedConnection.increment();
                case RANDOM_BREAKTHROUGH -> randomBreakthrough.increment();
            }
        }

        Counters snapshot() {
            return new Counters(
                channelsAttempted.sum(),
                channelsAccepted.sum(),
                blocksCarved.sum(),
                waterBlocks.sum(),
                sedimentBlocks.sum(),
                terrainMismatch.sum(),
                bedAboveSurface.sum(),
                bedTooDeep.sum(),
                caveIntersection.sum(),
                outOfBounds.sum(),
                0L,
                safeGround.sum(),
                thinRoof.sum(),
                caveEntrance.sum(),
                aquifer.sum(),
                allowedConnection.sum(),
                randomBreakthrough.sum(),
                basinColumnsAttempted.sum(),
                lakeColumnsCarved.sum(),
                basinWaterBlocks.sum(),
                shorelineColumns.sum(),
                overflowColumnsCarved.sum()
            );
        }
    }

    public record Counters(
        long channelsAttempted,
        long channelsAccepted,
        long blocksCarved,
        long waterBlocksPlaced,
        long sedimentBlocksPlaced,
        long rejectedTerrainMismatch,
        long rejectedBedAboveSurface,
        long rejectedBedTooDeep,
        long rejectedCaveIntersection,
        long outOfBoundsAttempts,
        long neighbourReads,
        long safeGround,
        long thinRoof,
        long caveEntrance,
        long aquifer,
        long allowedRiverCaveConnection,
        long randomBreakthrough,
        long basinColumnsAttempted,
        long lakeColumnsCarved,
        long basinWaterBlocksPlaced,
        long shorelineColumns,
        long overflowColumnsCarved
    ) {
        private static final Counters EMPTY = new Counters(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0
        );
    }
}
