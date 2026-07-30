package dev.nexusmc.landscape.worldgen.v2.hydrology;

import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Chunk-local finalization of analytically confirmed river columns.
 *
 * <p>This class never obtains another chunk and all reads/writes are constrained
 * to the supplied ChunkAccess.
 */
public final class RiverWaterPass {
    private static final LongAdder CHANNELS_ATTEMPTED = new LongAdder();
    private static final LongAdder CHANNELS_ACCEPTED = new LongAdder();
    private static final LongAdder BLOCKS_CARVED = new LongAdder();
    private static final LongAdder WATER_BLOCKS = new LongAdder();
    private static final LongAdder SEDIMENT_BLOCKS = new LongAdder();
    private static final LongAdder TERRAIN_MISMATCH = new LongAdder();
    private static final LongAdder BED_ABOVE_SURFACE = new LongAdder();
    private static final LongAdder BED_TOO_DEEP = new LongAdder();
    private static final LongAdder CAVE_INTERSECTION = new LongAdder();
    private static final LongAdder OUT_OF_BOUNDS = new LongAdder();
    private static final LongAdder NEIGHBOUR_READS = new LongAdder();

    private RiverWaterPass() {
    }

    public static void apply(ChunkAccess chunk, RandomState randomState) {
        NexusV2HydrologySampler sampler = new NexusV2HydrologySampler(randomState);
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] grid = {0, 4, 8, 12, 15};
        HydrologyMath.Sample[][] samples = new HydrologyMath.Sample[5][5];
        for (int gridZ = 0; gridZ < grid.length; gridZ++) {
            for (int gridX = 0; gridX < grid.length; gridX++) {
                samples[gridZ][gridX] = sampler.sample(
                    minX + grid[gridX],
                    minZ + grid[gridZ]
                );
            }
        }

        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = minZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = minX + localX;
                WaterColumn river = interpolate(samples, grid, localX, localZ);
                if (river.mask() < 0.62) {
                    continue;
                }
                CHANNELS_ATTEMPTED.increment();
                int surfaceY = chunk.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    worldX,
                    worldZ
                ) - 1;
                int bedY = (int)Math.floor(river.bedY());
                int waterY = (int)Math.floor(river.waterY());
                double expectedIncision = surfaceY - river.bedY();
                if (expectedIncision < 0.5) {
                    TERRAIN_MISMATCH.increment();
                    BED_ABOVE_SURFACE.increment();
                    continue;
                }
                if (expectedIncision > 14.0) {
                    TERRAIN_MISMATCH.increment();
                    BED_TOO_DEEP.increment();
                    continue;
                }
                waterY = Math.min(waterY, surfaceY - 1);
                if (waterY <= bedY) {
                    TERRAIN_MISMATCH.increment();
                    BED_ABOVE_SURFACE.increment();
                    continue;
                }
                if (bedY < minY + 3 || waterY > maxY - 2) {
                    OUT_OF_BOUNDS.increment();
                    continue;
                }
                if (intersectsUnrelatedCave(chunk, cursor, worldX, worldZ, bedY)) {
                    CAVE_INTERSECTION.increment();
                    continue;
                }

                for (int y = surfaceY; y > waterY; y--) {
                    cursor.set(worldX, y, worldZ);
                    if (!chunk.getBlockState(cursor).isAir()) {
                        chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
                        BLOCKS_CARVED.increment();
                    }
                }
                for (int y = bedY + 1; y <= waterY; y++) {
                    cursor.set(worldX, y, worldZ);
                    chunk.setBlockState(cursor, Blocks.WATER.defaultBlockState(), false);
                    WATER_BLOCKS.increment();
                }
                cursor.set(worldX, bedY, worldZ);
                chunk.setBlockState(
                    cursor,
                    river.order >= 3
                        ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.SAND.defaultBlockState(),
                    false
                );
                SEDIMENT_BLOCKS.increment();
                cursor.set(worldX, bedY - 1, worldZ);
                chunk.setBlockState(cursor, Blocks.CLAY.defaultBlockState(), false);
                SEDIMENT_BLOCKS.increment();
                CHANNELS_ACCEPTED.increment();
            }
        }
    }

    private static WaterColumn interpolate(
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
        HydrologyMath.Sample a = samples[z0][x0];
        HydrologyMath.Sample b = samples[z0][x1];
        HydrologyMath.Sample c = samples[z1][x0];
        HydrologyMath.Sample d = samples[z1][x1];
        return new WaterColumn(
            bilerp(a.mask(), b.mask(), c.mask(), d.mask(), tx, tz),
            bilerp(a.bedY(), b.bedY(), c.bedY(), d.bedY(), tx, tz),
            bilerp(a.waterY(), b.waterY(), c.waterY(), d.waterY(), tx, tz),
            Math.max(Math.max(a.order(), b.order()), Math.max(c.order(), d.order()))
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

    private static boolean intersectsUnrelatedCave(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int worldX,
        int worldZ,
        int bedY
    ) {
        for (int y = bedY - 3; y < bedY; y++) {
            cursor.set(worldX, y, worldZ);
            if (chunk.getBlockState(cursor).isAir()
                || !chunk.getFluidState(cursor).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static Counters counters() {
        return new Counters(
            CHANNELS_ATTEMPTED.sum(),
            CHANNELS_ACCEPTED.sum(),
            BLOCKS_CARVED.sum(),
            WATER_BLOCKS.sum(),
            SEDIMENT_BLOCKS.sum(),
            TERRAIN_MISMATCH.sum(),
            BED_ABOVE_SURFACE.sum(),
            BED_TOO_DEEP.sum(),
            CAVE_INTERSECTION.sum(),
            OUT_OF_BOUNDS.sum(),
            NEIGHBOUR_READS.sum()
        );
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
        long neighbourReads
    ) {
    }

    private record WaterColumn(
        double mask,
        double bedY,
        double waterY,
        int order
    ) {
    }
}
