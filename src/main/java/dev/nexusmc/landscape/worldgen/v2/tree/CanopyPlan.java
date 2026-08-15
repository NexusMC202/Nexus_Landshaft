package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable foliage plan resolved after the branch graph and before voxelization.
 * The voxelizer only materializes these clusters; species-specific canopy
 * decisions live here.
 */
public record CanopyPlan(List<Cluster> clusters) {
    public CanopyPlan {
        clusters = List.copyOf(clusters);
    }

    public static CanopyPlan resolve(
        BranchGraph graph,
        AnatomyPlan anatomy
    ) {
        if (graph == null || anatomy == null) {
            throw new IllegalArgumentException("graph and anatomy are required");
        }
        double crownTop = crownTop(graph);
        List<Cluster> clusters = new ArrayList<>();
        for (BranchGraph.Segment segment : graph.segments()) {
            if (!segment.role().canCarryFoliage() || segment.endRadius() > 0.72) {
                continue;
            }
            Cluster cluster = anatomy.species() == ConiferSpeciesProfile.PINE
                ? pineCluster(segment, crownTop, anatomy)
                : spruceCluster(segment, crownTop, anatomy);
            if (cluster != null) {
                clusters.add(cluster);
            }
        }
        return new CanopyPlan(clusters);
    }

    private static Cluster spruceCluster(
        BranchGraph.Segment segment,
        double crownTop,
        AnatomyPlan anatomy
    ) {
        long hash = foliageHash(anatomy.seed(), segment.id(), anatomy.species());
        double relativeHeight = crownTop <= 0.0
            ? 1.0
            : clamp(segment.endY() / crownTop, 0.0, 1.0);
        double windDensity = windDensityMultiplier(segment, anatomy.environment());
        double density = anatomy.species().foliageDensityMultiplier() * windDensity;
        double horizontalChance = clamp(0.68 * density, 0.30, 0.94);
        int horizontal = 1 + (unit(hash) < horizontalChance ? 1 : 0);
        int verticalDown = 1 + (relativeHeight < 0.72 && windDensity > 0.74 ? 1 : 0);
        int verticalUp = relativeHeight > 0.80 ? 2 : 1;
        return new Cluster(
            segment.id(),
            ClusterKind.SPRUCE_MASS,
            (int)Math.round(segment.endX()),
            (int)Math.round(segment.endY() - (1.0 - relativeHeight) * 0.45),
            (int)Math.round(segment.endZ()),
            horizontal,
            verticalDown,
            verticalUp,
            density,
            hash
        );
    }

    private static Cluster pineCluster(
        BranchGraph.Segment segment,
        double crownTop,
        AnatomyPlan anatomy
    ) {
        double relativeHeight = crownTop <= 0.0
            ? 1.0
            : clamp(segment.endY() / crownTop, 0.0, 1.0);
        if (relativeHeight < 0.54) {
            return null;
        }
        long hash = foliageHash(anatomy.seed(), segment.id(), anatomy.species());
        double windDensity = windDensityMultiplier(segment, anatomy.environment());
        double acceptance = (0.52
            + (relativeHeight - 0.54) * 0.82
            * anatomy.species().upperCrownMultiplier())
            * clamp(windDensity, 0.72, 1.18);
        acceptance = clamp(acceptance, 0.28, 0.98);
        if (relativeHeight < 0.74 && unit(hash) > acceptance) {
            return null;
        }
        if (windDensity < 0.80
            && relativeHeight < 0.86
            && unit(mix(hash ^ 0x57494E4443414E4FL)) > windDensity) {
            return null;
        }
        int horizontal = relativeHeight > 0.76 ? 2 : 1;
        if (unit(mix(hash)) > 0.72 / clamp(windDensity, 0.72, 1.18)) {
            horizontal++;
        }
        horizontal = Math.min(horizontal, 3);
        int vertical = relativeHeight > 0.84 ? 2 : 1;
        return new Cluster(
            segment.id(),
            ClusterKind.PINE_CLUSTER,
            (int)Math.round(segment.endX()),
            (int)Math.round(segment.endY()),
            (int)Math.round(segment.endZ()),
            horizontal,
            vertical,
            vertical,
            anatomy.species().foliageDensityMultiplier() * windDensity,
            hash
        );
    }

    private static double windDensityMultiplier(
        BranchGraph.Segment segment,
        TreeEnvironment environment
    ) {
        return WindAsymmetryPolicy.canopyDensityMultiplier(
            environment,
            segment.endX() - segment.startX(),
            segment.endZ() - segment.startZ()
        );
    }

    private static double crownTop(BranchGraph graph) {
        double top = 0.0;
        for (BranchGraph.Segment segment : graph.segments()) {
            if (segment.role().canCarryFoliage()) {
                top = Math.max(top, Math.max(segment.startY(), segment.endY()));
            }
        }
        return top;
    }

    private static long foliageHash(
        long seed,
        int segmentId,
        ConiferSpeciesProfile species
    ) {
        return mix(seed
            ^ ((long)segmentId * 0x9E3779B97F4A7C15L)
            ^ ((long)species.ordinal() * 0xD1B54A32D192ED03L));
    }

    static long localHash(long hash, int dx, int dy, int dz) {
        return mix(hash
            ^ ((long)dx * 0x632BE59BD9B4E019L)
            ^ ((long)dy * 0x94D049BB133111EBL)
            ^ ((long)dz * 0xC2B2AE3D27D4EB4FL));
    }

    static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum ClusterKind {
        SPRUCE_MASS,
        PINE_CLUSTER
    }

    public record Cluster(
        int sourceSegmentId,
        ClusterKind kind,
        int centerX,
        int centerY,
        int centerZ,
        int horizontalRadius,
        int verticalDown,
        int verticalUp,
        double density,
        long densitySeed
    ) {
        public Cluster {
            if (sourceSegmentId < 0 || kind == null) {
                throw new IllegalArgumentException("invalid canopy cluster identity");
            }
            if (horizontalRadius < 1 || horizontalRadius > 4
                || verticalDown < 1 || verticalDown > 3
                || verticalUp < 1 || verticalUp > 3) {
                throw new IllegalArgumentException("invalid canopy cluster extent");
            }
            if (!Double.isFinite(density) || density <= 0.0) {
                throw new IllegalArgumentException("invalid canopy density");
            }
        }
    }
}
