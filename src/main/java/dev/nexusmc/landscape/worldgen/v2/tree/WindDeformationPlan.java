package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic post-growth deformation driven by the local wind field.
 * Shared graph points are transformed only from their coordinates and the
 * environment, so parent/child joints remain attached.
 */
public record WindDeformationPlan(
    double directionX,
    double directionZ,
    double strength,
    double maxLateralShift
) {
    public WindDeformationPlan {
        if (!Double.isFinite(directionX) || !Double.isFinite(directionZ)
            || !Double.isFinite(strength) || !Double.isFinite(maxLateralShift)
            || strength < 0.0 || strength > 1.0 || maxLateralShift < 0.0) {
            throw new IllegalArgumentException("invalid wind deformation plan");
        }
    }

    public static WindDeformationPlan resolve(AnatomyPlan anatomy) {
        if (anatomy == null) {
            throw new IllegalArgumentException("anatomy plan is required");
        }
        TreeEnvironment environment = anatomy.environment();
        double wind = environment.windStrength();
        if (wind < 0.08) {
            return new WindDeformationPlan(0.0, 0.0, 0.0, 0.0);
        }
        double directionX = environment.windX() / wind;
        double directionZ = environment.windZ() / wind;
        double exposure = clamp(
            0.46 + environment.openSpaceStrength() * 0.34
                + environment.slope() * 0.20,
            0.40,
            1.0
        );
        double strength = clamp(wind * exposure, 0.0, 1.0);
        double tierShift = switch (anatomy.quality()) {
            case BASIC -> 0.85;
            case MID -> 1.45;
            case HERO -> 2.20;
        };
        return new WindDeformationPlan(
            directionX,
            directionZ,
            strength,
            tierShift * strength
        );
    }

    public BranchGraph apply(BranchGraph graph, AnatomyPlan anatomy) {
        if (graph == null || anatomy == null) {
            throw new IllegalArgumentException("graph and anatomy are required");
        }
        if (strength == 0.0 || maxLateralShift == 0.0) {
            return graph;
        }
        double height = Math.max(1.0, anatomy.trunk().height());
        double radiusLimit = anatomy.quality().budget().maxHorizontalRadius() - 0.35;
        List<BranchGraph.Segment> transformed = new ArrayList<>(graph.segments().size());
        for (BranchGraph.Segment segment : graph.segments()) {
            Point start = deform(
                segment.startX(), segment.startY(), segment.startZ(), height, radiusLimit
            );
            Point end = deform(
                segment.endX(), segment.endY(), segment.endZ(), height, radiusLimit
            );
            transformed.add(new BranchGraph.Segment(
                segment.id(),
                segment.parentId(),
                start.x(),
                start.y(),
                start.z(),
                end.x(),
                end.y(),
                end.z(),
                segment.startRadius(),
                segment.endRadius(),
                segment.role()
            ));
        }
        return new BranchGraph(transformed);
    }

    private Point deform(
        double x,
        double y,
        double z,
        double treeHeight,
        double radiusLimit
    ) {
        if (y <= 0.0) {
            return new Point(x, y, z);
        }
        double normalized = clamp(y / treeHeight, 0.0, 1.12);
        double response = normalized * normalized;
        double shift = maxLateralShift * response;
        double movedX = x + directionX * shift;
        double movedZ = z + directionZ * shift;
        double radial = Math.hypot(movedX, movedZ);
        if (radial > radiusLimit && radial > 0.0) {
            double scale = radiusLimit / radial;
            movedX *= scale;
            movedZ *= scale;
        }
        return new Point(movedX, y, movedZ);
    }

    public int conservativeExtraRadius() {
        return (int)Math.ceil(maxLateralShift);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Point(double x, double y, double z) {
    }
}
