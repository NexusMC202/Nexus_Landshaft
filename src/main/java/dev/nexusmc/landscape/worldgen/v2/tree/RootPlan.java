package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans visible structural roots before voxelization. Conifers in dense forest
 * stay narrow at the base, while old exposed trees receive broader anchoring
 * roots with slope and wind bias.
 */
public record RootPlan(List<RootArm> arms, double buttressRadius) {
    public RootPlan {
        arms = List.copyOf(arms);
        if (arms.size() > 12) {
            throw new IllegalArgumentException("too many root arms: " + arms.size());
        }
        if (!Double.isFinite(buttressRadius) || buttressRadius < 0.0) {
            throw new IllegalArgumentException("invalid buttress radius");
        }
    }

    public static RootPlan resolve(
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history,
        TrunkPlan trunk
    ) {
        if (quality == null || environment == null || history == null || trunk == null) {
            throw new IllegalArgumentException("root planning inputs are required");
        }

        double openness = environment.openSpaceStrength();
        double anchoringDemand = environment.slope() * 0.34
            + environment.windStrength() * 0.28
            + openness * 0.20
            + history.vigor() * 0.10
            + (history.ageYears() >= 90 ? 0.12 : 0.0);
        anchoringDemand = clamp(anchoringDemand, 0.0, 1.0);

        int count = switch (quality) {
            case BASIC -> anchoringDemand > 0.56 ? 2 : 0;
            case MID -> 2 + (anchoringDemand > 0.48 ? 1 : 0);
            case HERO -> 3 + (anchoringDemand > 0.42 ? 2 : 1);
        };
        if (environment.forestCompetition() > 0.78) {
            count = Math.max(0, count - 1);
        }

        double baseLength = switch (quality) {
            case BASIC -> 1.4;
            case MID -> 2.5;
            case HERO -> 4.2;
        };
        baseLength *= 0.70 + anchoringDemand * 0.65;
        baseLength *= 1.0 - environment.forestCompetition() * 0.22;

        List<RootArm> arms = new ArrayList<>();
        long state = TreeLifeHistory.mix(seed ^ 0x524F4F54504C414EL);
        double biasX = -environment.windX() * 0.45
            - environment.openSpaceX() * environment.slope() * 0.30;
        double biasZ = -environment.windZ() * 0.45
            - environment.openSpaceZ() * environment.slope() * 0.30;
        double biasAngle = Math.atan2(biasZ, biasX);

        for (int index = 0; index < count; index++) {
            state = TreeLifeHistory.mix(state);
            double jitter = (TreeLifeHistory.unit(state) - 0.5) * 0.72;
            double angle = Math.PI * 2.0 * index / Math.max(1, count) + jitter;
            if (index == 0 && anchoringDemand > 0.45) {
                angle = biasAngle + jitter * 0.35;
            }
            state = TreeLifeHistory.mix(state);
            double length = baseLength * (0.78 + TreeLifeHistory.unit(state) * 0.38);
            double depth = quality == TreeQualityTier.HERO ? -0.65 : -0.35;
            depth -= environment.slope() * 0.35;
            double startRadius = Math.max(0.24, trunk.baseRadius() * 0.48);
            double endRadius = Math.max(0.08, startRadius * 0.22);
            arms.add(new RootArm(
                Math.cos(angle) * length,
                depth,
                Math.sin(angle) * length,
                startRadius,
                endRadius
            ));
        }

        double buttressRadius = trunk.baseRadius()
            * (0.08 + anchoringDemand * 0.22)
            * (quality == TreeQualityTier.HERO ? 1.20 : 1.0);
        return new RootPlan(arms, buttressRadius);
    }

    public record RootArm(
        double endX,
        double endY,
        double endZ,
        double startRadius,
        double endRadius
    ) {
        public RootArm {
            requireFinite(endX, "endX");
            requireFinite(endY, "endY");
            requireFinite(endZ, "endZ");
            requireFinite(startRadius, "startRadius");
            requireFinite(endRadius, "endRadius");
            if (startRadius <= 0.0 || endRadius < 0.0 || endRadius >= startRadius) {
                throw new IllegalArgumentException("invalid root taper");
            }
            if (Math.sqrt(endX * endX + endZ * endZ) < 0.65) {
                throw new IllegalArgumentException("root arm is too short");
            }
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
