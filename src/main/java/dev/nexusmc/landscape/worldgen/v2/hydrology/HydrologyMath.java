package dev.nexusmc.landscape.worldgen.v2.hydrology;

/**
 * A canonical drainage graph made from jittered watershed nodes. Each node
 * selects exactly one lower neighbour, producing converging trees and local
 * sink basins without reading generated chunks.
 */
public final class HydrologyMath {
    public static final int BASIN_SIZE = 2_048;
    private static final double NODE_JITTER = 0.31;
    private static final double BASE_HALF_WIDTH = 17.0;

    private HydrologyMath() {
    }

    public static Sample sample(int blockX, int blockZ, NoiseSource noise) {
        int centerCellX = Math.floorDiv(blockX, BASIN_SIZE);
        int centerCellZ = Math.floorDiv(blockZ, BASIN_SIZE);
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestWaterLevel = 63.0;
        int bestOrder = 0;
        double bestFlowX = 0.0;
        double bestFlowZ = 1.0;
        Node bestSource = null;
        Node bestTarget = null;

        for (int cellZ = centerCellZ - 2; cellZ <= centerCellZ + 2; cellZ++) {
            for (int cellX = centerCellX - 2; cellX <= centerCellX + 2; cellX++) {
                Node source = node(cellX, cellZ, noise);
                Node target = downstream(source, noise);
                if (target == null) {
                    continue;
                }
                Curve curve = curve(source, target, noise);
                Projection projection = project(blockX, blockZ, curve);
                if (projection.distance >= bestDistance) {
                    continue;
                }
                bestDistance = projection.distance;
                bestWaterLevel = lerp(source.level, target.level, projection.t);
                bestSource = source;
                bestTarget = target;
                double tangentX = tangentX(curve, projection.t);
                double tangentZ = tangentZ(curve, projection.t);
                double length = Math.max(1.0E-9, Math.hypot(tangentX, tangentZ));
                bestFlowX = tangentX / length;
                bestFlowZ = tangentZ / length;
            }
        }

        if (bestSource != null) {
            bestOrder = streamOrder(bestSource, bestTarget, noise);
        }
        double halfWidth = BASE_HALF_WIDTH + bestOrder * 7.0;
        double mask = 1.0 - smoothstep(halfWidth, halfWidth * 4.2, bestDistance);
        return new Sample(
            bestDistance,
            clamp01(mask),
            bestOrder,
            bestWaterLevel,
            bestFlowX,
            bestFlowZ
        );
    }

    public static Node node(int cellX, int cellZ, NoiseSource noise) {
        double centerX = (cellX + 0.5) * BASIN_SIZE;
        double centerZ = (cellZ + 0.5) * BASIN_SIZE;
        double jitterX = noise.layout(cellX * 0.731 + 17.0, cellZ * 0.731 - 11.0);
        double jitterZ = noise.layout(cellX * 0.731 - 43.0, cellZ * 0.731 + 29.0);
        double x = centerX + clamp(jitterX, -1.0, 1.0) * BASIN_SIZE * NODE_JITTER;
        double z = centerZ + clamp(jitterZ, -1.0, 1.0) * BASIN_SIZE * NODE_JITTER;
        double regionalLevel = noise.elevation(x / 8_192.0, z / 8_192.0);
        double localLevel = noise.layout(
            cellX * 3.173 + cellZ * 0.137 + 101.0,
            cellZ * 2.917 - cellX * 0.193 - 67.0
        );
        double levelNoise = clamp(
            regionalLevel * 0.86 + localLevel * 0.14,
            -1.0,
            1.0
        );
        return new Node(cellX, cellZ, x, z, 70.0 + levelNoise * 22.0);
    }

    public static Node downstream(Node source, NoiseSource noise) {
        Node best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Node candidate = node(source.cellX + dx, source.cellZ + dz, noise);
                if (candidate.level >= source.level - 0.35) {
                    continue;
                }
                double diagonalPenalty = dx != 0 && dz != 0 ? 1.2 : 0.0;
                double score = candidate.level + diagonalPenalty;
                if (score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static int streamOrder(Node source, Node target, NoiseSource noise) {
        int incoming = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Node neighbour = node(source.cellX + dx, source.cellZ + dz, noise);
                Node neighbourTarget = downstream(neighbour, noise);
                if (neighbourTarget != null
                    && neighbourTarget.cellX == source.cellX
                    && neighbourTarget.cellZ == source.cellZ) {
                    incoming++;
                }
            }
        }
        double drop = source.level - target.level;
        return Math.min(3, 1 + (incoming >= 2 ? 1 : 0) + (incoming >= 4 || drop > 16.0 ? 1 : 0));
    }

    private static Curve curve(Node source, Node target, NoiseSource noise) {
        double midpointX = (source.x + target.x) * 0.5;
        double midpointZ = (source.z + target.z) * 0.5;
        double dx = target.x - source.x;
        double dz = target.z - source.z;
        double length = Math.max(1.0E-9, Math.hypot(dx, dz));
        double bend = noise.tributary(
            midpointX / 3_072.0,
            midpointZ / 3_072.0
        ) * Math.min(520.0, length * 0.28);
        return new Curve(
            source.x,
            source.z,
            midpointX - dz / length * bend,
            midpointZ + dx / length * bend,
            target.x,
            target.z
        );
    }

    private static Projection project(double x, double z, Curve curve) {
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestT = 0.0;
        double previousX = curve.startX;
        double previousZ = curve.startZ;
        final int subdivisions = 12;
        for (int index = 1; index <= subdivisions; index++) {
            double endT = index / (double)subdivisions;
            double endX = curveX(curve, endT);
            double endZ = curveZ(curve, endT);
            double dx = endX - previousX;
            double dz = endZ - previousZ;
            double localT = clamp(
                ((x - previousX) * dx + (z - previousZ) * dz)
                    / Math.max(1.0E-9, dx * dx + dz * dz),
                0.0,
                1.0
            );
            double nearestX = lerp(previousX, endX, localT);
            double nearestZ = lerp(previousZ, endZ, localT);
            double distance = Math.hypot(x - nearestX, z - nearestZ);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestT = (index - 1 + localT) / subdivisions;
            }
            previousX = endX;
            previousZ = endZ;
        }
        return new Projection(bestDistance, bestT);
    }

    private static double curveX(Curve curve, double t) {
        double inverse = 1.0 - t;
        return inverse * inverse * curve.startX
            + 2.0 * inverse * t * curve.controlX
            + t * t * curve.endX;
    }

    private static double curveZ(Curve curve, double t) {
        double inverse = 1.0 - t;
        return inverse * inverse * curve.startZ
            + 2.0 * inverse * t * curve.controlZ
            + t * t * curve.endZ;
    }

    private static double tangentX(Curve curve, double t) {
        return 2.0 * (1.0 - t) * (curve.controlX - curve.startX)
            + 2.0 * t * (curve.endX - curve.controlX);
    }

    private static double tangentZ(Curve curve, double t) {
        return 2.0 * (1.0 - t) * (curve.controlZ - curve.startZ)
            + 2.0 * t * (curve.endZ - curve.controlZ);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public interface NoiseSource {
        double layout(double x, double z);

        double tributary(double x, double z);

        double elevation(double x, double z);
    }

    public record Sample(
        double distance,
        double mask,
        int order,
        double waterLevel,
        double flowX,
        double flowZ
    ) {
    }

    public record Node(
        int cellX,
        int cellZ,
        double x,
        double z,
        double level
    ) {
    }

    private record Curve(
        double startX,
        double startZ,
        double controlX,
        double controlZ,
        double endX,
        double endZ
    ) {
    }

    private record Projection(double distance, double t) {
    }
}
