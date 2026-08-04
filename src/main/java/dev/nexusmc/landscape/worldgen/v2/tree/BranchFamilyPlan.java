package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.List;

/**
 * Describes anatomically distinct branch families before skeleton generation.
 * Each family owns a vertical band, count, length curve, droop and foliage
 * behavior. This prevents every branch from being produced by one generic
 * random loop.
 */
public record BranchFamilyPlan(List<Family> families) {
    public BranchFamilyPlan {
        families = List.copyOf(families);
        if (families.isEmpty()) {
            throw new IllegalArgumentException("branch families are required");
        }
    }

    public static BranchFamilyPlan resolve(
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history,
        TrunkPlan trunk
    ) {
        if (quality == null || environment == null || history == null || trunk == null) {
            throw new IllegalArgumentException("branch planning inputs are required");
        }

        double crownStart = trunk.crownStart();
        double competition = environment.forestCompetition();
        double openness = environment.openSpaceStrength();
        double loss = history.crownLoss();

        int lowerCount = switch (quality) {
            case BASIC -> 1;
            case MID -> 2;
            case HERO -> 3;
        };
        lowerCount = Math.max(0, lowerCount - (int)Math.round(competition * 1.5));

        int middleCount = switch (quality) {
            case BASIC -> 3;
            case MID -> 6;
            case HERO -> 10;
        };
        middleCount = Math.max(2, (int)Math.round(middleCount * (1.0 - loss * 0.36)));

        int upperCount = switch (quality) {
            case BASIC -> 2;
            case MID -> 4;
            case HERO -> 7;
        };

        int deadCount = (int)Math.round(
            (middleCount + lowerCount) * history.deadBranchShare() * 0.70
        );
        if (history.leaderDamage() > 0.55) {
            deadCount++;
        }

        double broadness = 0.82 + openness * 0.30 - competition * 0.18;
        double middleLength = switch (quality) {
            case BASIC -> 2.6;
            case MID -> 4.3;
            case HERO -> 6.7;
        };
        middleLength *= broadness;

        long state = TreeLifeHistory.mix(seed ^ 0x4252414E43484641L);
        state = TreeLifeHistory.mix(state);
        double phase = TreeLifeHistory.unit(state) * Math.PI * 2.0;

        return new BranchFamilyPlan(List.of(
            new Family(
                Kind.LOWER_SPARSE,
                clamp(crownStart, 0.15, 0.58),
                clamp(crownStart + 0.16, 0.28, 0.70),
                lowerCount,
                middleLength * 0.72,
                0.24,
                0.30,
                phase,
                true,
                0.18
            ),
            new Family(
                Kind.MIDDLE_STRUCTURAL,
                clamp(crownStart + 0.10, 0.24, 0.68),
                0.76,
                middleCount,
                middleLength,
                0.13,
                0.22,
                phase + 1.31,
                true,
                0.04
            ),
            new Family(
                Kind.UPPER_SHORT,
                0.68,
                0.94,
                upperCount,
                middleLength * 0.48,
                0.05,
                0.10,
                phase + 2.17,
                true,
                0.02
            ),
            new Family(
                Kind.DEAD_AND_DAMAGED,
                clamp(crownStart, 0.18, 0.62),
                0.82,
                deadCount,
                middleLength * 0.62,
                0.18,
                0.28,
                phase + 0.67,
                false,
                1.0
            )
        ));
    }

    public enum Kind {
        LOWER_SPARSE,
        MIDDLE_STRUCTURAL,
        UPPER_SHORT,
        DEAD_AND_DAMAGED
    }

    public record Family(
        Kind kind,
        double minVertical,
        double maxVertical,
        int targetCount,
        double baseLength,
        double upwardLift,
        double droop,
        double phase,
        boolean foliageBearing,
        double deadProbability
    ) {
        public Family {
            if (kind == null) {
                throw new IllegalArgumentException("branch family kind is required");
            }
            requireFinite(minVertical, "minVertical");
            requireFinite(maxVertical, "maxVertical");
            requireFinite(baseLength, "baseLength");
            requireFinite(upwardLift, "upwardLift");
            requireFinite(droop, "droop");
            requireFinite(phase, "phase");
            requireFinite(deadProbability, "deadProbability");
            if (minVertical < 0.0 || maxVertical > 1.0 || minVertical >= maxVertical) {
                throw new IllegalArgumentException("invalid branch vertical band");
            }
            if (targetCount < 0 || targetCount > 64) {
                throw new IllegalArgumentException("invalid branch target count");
            }
            if (baseLength <= 0.0) {
                throw new IllegalArgumentException("branch length must be positive");
            }
            if (deadProbability < 0.0 || deadProbability > 1.0) {
                throw new IllegalArgumentException("deadProbability outside [0,1]");
            }
        }

        public double verticalAt(int index, long seed) {
            if (targetCount <= 1) {
                return (minVertical + maxVertical) * 0.5;
            }
            double regular = index / (double)(targetCount - 1);
            long mixed = TreeLifeHistory.mix(seed ^ (kind.ordinal() * 0x9E3779B97F4A7C15L) ^ index);
            double jitter = (TreeLifeHistory.unit(mixed) - 0.5)
                * (maxVertical - minVertical)
                / Math.max(3.0, targetCount);
            return clamp(minVertical + (maxVertical - minVertical) * regular + jitter,
                minVertical, maxVertical);
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
