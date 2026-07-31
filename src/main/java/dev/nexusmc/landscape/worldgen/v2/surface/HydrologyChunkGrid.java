package dev.nexusmc.landscape.worldgen.v2.surface;

import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.NexusV2HydrologySampler;

/**
 * Chunk-local interpolation of continuous Stage 5 hydrology influences.
 * Physical channels remain owned by RiverWaterPass; Stage 6 only needs smooth
 * masks for surface and vegetation selection.
 */
public final class HydrologyChunkGrid {
    private static final int STEP = 4;
    private static final int SIZE = 6;
    private final int minX;
    private final int minZ;
    private final HydrologyMath.Sample[][] rivers =
        new HydrologyMath.Sample[SIZE][SIZE];
    private final HydrologyMath.BasinSample[][] basins =
        new HydrologyMath.BasinSample[SIZE][SIZE];

    public HydrologyChunkGrid(
        int chunkMinX,
        int chunkMinZ,
        NexusV2HydrologySampler sampler
    ) {
        this.minX = chunkMinX;
        this.minZ = chunkMinZ;
        for (int gridZ = 0; gridZ < SIZE; gridZ++) {
            for (int gridX = 0; gridX < SIZE; gridX++) {
                int x = minX + gridX * STEP;
                int z = minZ + gridZ * STEP;
                rivers[gridZ][gridX] = sampler.sample(x, z);
                basins[gridZ][gridX] = sampler.basinSample(x, z);
            }
        }
    }

    public HydrologyMath.Sample river(int x, int z) {
        Cell cell = cell(x, z);
        HydrologyMath.Sample a = rivers[cell.z0()][cell.x0()];
        HydrologyMath.Sample b = rivers[cell.z0()][cell.x1()];
        HydrologyMath.Sample c = rivers[cell.z1()][cell.x0()];
        HydrologyMath.Sample d = rivers[cell.z1()][cell.x1()];
        HydrologyMath.Sample nearest = rivers[cell.nearestZ()][cell.nearestX()];
        return new HydrologyMath.Sample(
            bilerp(a.distance(), b.distance(), c.distance(), d.distance(), cell),
            bilerp(a.signedDistance(), b.signedDistance(), c.signedDistance(), d.signedDistance(), cell),
            clamp01(bilerp(a.mask(), b.mask(), c.mask(), d.mask(), cell)),
            nearest.order(),
            nearest.accumulation(),
            nearest.canonicalSegmentId(),
            bilerp(a.bedY(), b.bedY(), c.bedY(), d.bedY(), cell),
            bilerp(a.waterY(), b.waterY(), c.waterY(), d.waterY(), cell),
            bilerp(a.flowX(), b.flowX(), c.flowX(), d.flowX(), cell),
            bilerp(a.flowZ(), b.flowZ(), c.flowZ(), d.flowZ(), cell)
        );
    }

    public HydrologyMath.BasinSample basin(int x, int z) {
        Cell cell = cell(x, z);
        HydrologyMath.BasinSample a = basins[cell.z0()][cell.x0()];
        HydrologyMath.BasinSample b = basins[cell.z0()][cell.x1()];
        HydrologyMath.BasinSample c = basins[cell.z1()][cell.x0()];
        HydrologyMath.BasinSample d = basins[cell.z1()][cell.x1()];
        HydrologyMath.BasinSample nearest =
            basins[cell.nearestZ()][cell.nearestX()];
        return new HydrologyMath.BasinSample(
            nearest.reason(),
            nearest.basinId(),
            nearest.outletId(),
            bilerp(a.radialDistance(), b.radialDistance(), c.radialDistance(), d.radialDistance(), cell),
            clamp01(bilerp(a.mask(), b.mask(), c.mask(), d.mask(), cell)),
            clamp01(bilerp(
                a.shorelineWeight(),
                b.shorelineWeight(),
                c.shorelineWeight(),
                d.shorelineWeight(),
                cell
            )),
            bilerp(a.bedY(), b.bedY(), c.bedY(), d.bedY(), cell),
            bilerp(a.waterY(), b.waterY(), c.waterY(), d.waterY(), cell),
            nearest.closedBasin()
        );
    }

    private Cell cell(int x, int z) {
        double gx = clamp((x - minX) / (double)STEP, 0.0, SIZE - 1.000001);
        double gz = clamp((z - minZ) / (double)STEP, 0.0, SIZE - 1.000001);
        int x0 = (int)Math.floor(gx);
        int z0 = (int)Math.floor(gz);
        int x1 = Math.min(SIZE - 1, x0 + 1);
        int z1 = Math.min(SIZE - 1, z0 + 1);
        double tx = gx - x0;
        double tz = gz - z0;
        return new Cell(
            x0,
            x1,
            z0,
            z1,
            tx,
            tz,
            tx < 0.5 ? x0 : x1,
            tz < 0.5 ? z0 : z1
        );
    }

    private static double bilerp(
        double a,
        double b,
        double c,
        double d,
        Cell cell
    ) {
        if (!Double.isFinite(a)
            || !Double.isFinite(b)
            || !Double.isFinite(c)
            || !Double.isFinite(d)) {
            return riversafe(a, b, c, d);
        }
        double top = a + (b - a) * cell.tx();
        double bottom = c + (d - c) * cell.tx();
        return top + (bottom - top) * cell.tz();
    }

    private static double riversafe(double... values) {
        for (double value : values) {
            if (Double.isFinite(value)) {
                return value;
            }
        }
        return 0.0;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private record Cell(
        int x0,
        int x1,
        int z0,
        int z1,
        double tx,
        double tz,
        int nearestX,
        int nearestZ
    ) {
    }
}
