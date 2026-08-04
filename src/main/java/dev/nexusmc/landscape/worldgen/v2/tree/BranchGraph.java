package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable structural skeleton produced before voxelization. */
public record BranchGraph(List<Segment> segments) {
    public BranchGraph {
        segments = List.copyOf(segments);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("branch graph is empty");
        }
        validate(segments);
    }

    private static void validate(List<Segment> segments) {
        Set<Integer> ids = new HashSet<>();
        Set<Integer> completed = new HashSet<>();
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            if (!ids.add(segment.id())) {
                throw new IllegalArgumentException("duplicate branch id: " + segment.id());
            }
            if (index == 0) {
                if (segment.parentId() != -1) {
                    throw new IllegalArgumentException("root branch must use parent -1");
                }
            } else if (!completed.contains(segment.parentId())) {
                throw new IllegalArgumentException(
                    "parent must precede child: " + segment.parentId()
                );
            }
            completed.add(segment.id());
        }
    }

    public long fingerprint() {
        long hash = 0xCBF29CE484222325L;
        for (Segment segment : segments) {
            hash = mix(hash, segment.id());
            hash = mix(hash, segment.parentId());
            hash = mix(hash, Double.doubleToLongBits(segment.startX()));
            hash = mix(hash, Double.doubleToLongBits(segment.startY()));
            hash = mix(hash, Double.doubleToLongBits(segment.startZ()));
            hash = mix(hash, Double.doubleToLongBits(segment.endX()));
            hash = mix(hash, Double.doubleToLongBits(segment.endY()));
            hash = mix(hash, Double.doubleToLongBits(segment.endZ()));
            hash = mix(hash, Double.doubleToLongBits(segment.startRadius()));
            hash = mix(hash, Double.doubleToLongBits(segment.endRadius()));
            hash = mix(hash, segment.dead() ? 1 : 0);
        }
        return hash;
    }

    public List<Segment> childrenOf(int parentId) {
        List<Segment> result = new ArrayList<>();
        for (Segment segment : segments) {
            if (segment.parentId() == parentId) {
                result.add(segment);
            }
        }
        return List.copyOf(result);
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    public record Segment(
        int id,
        int parentId,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        double startRadius,
        double endRadius,
        boolean dead
    ) {
        public Segment {
            finite(startX, "startX");
            finite(startY, "startY");
            finite(startZ, "startZ");
            finite(endX, "endX");
            finite(endY, "endY");
            finite(endZ, "endZ");
            if (!Double.isFinite(startRadius) || !Double.isFinite(endRadius)
                || startRadius <= 0.0 || endRadius < 0.0
                || endRadius > startRadius) {
                throw new IllegalArgumentException("invalid branch radii");
            }
            double dx = endX - startX;
            double dy = endY - startY;
            double dz = endZ - startZ;
            if (Math.sqrt(square(dx) + square(dy) + square(dz)) < 0.25) {
                throw new IllegalArgumentException("branch segment is too short");
            }
        }

        public double length() {
            return Math.sqrt(
                square(endX - startX)
                    + square(endY - startY)
                    + square(endZ - startZ)
            );
        }

        private static double square(double value) {
            return value * value;
        }

        private static void finite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " is not finite");
            }
        }
    }
}
