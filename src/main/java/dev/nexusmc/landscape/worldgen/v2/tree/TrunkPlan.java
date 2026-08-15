package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Anatomical plan for the main trunk. It is resolved before any branch graph
 * is created, so height, taper, crown start and leader structure are coherent
 * consequences of species role, age and environment rather than local random
 * decisions made while placing segments.
 */
public record TrunkPlan(
    int height,
    int sections,
    double baseRadius,
    double crownStart,
    double leanX,
    double leanZ,
    boolean secondaryLeader,
    double secondaryLeaderStart
) {
    public TrunkPlan {
        if (height < 4) {
            throw new IllegalArgumentException("trunk height below 4: " + height);
        }
        if (sections < 2 || sections > height) {
            throw new IllegalArgumentException("invalid trunk sections: " + sections);
        }
        requireFinite(baseRadius, "baseRadius");
        requireFinite(crownStart, "crownStart");
        requireFinite(leanX, "leanX");
        requireFinite(leanZ, "leanZ");
        requireFinite(secondaryLeaderStart, "secondaryLeaderStart");
        if (baseRadius <= 0.0) {
            throw new IllegalArgumentException("base radius must be positive");
        }
        if (crownStart < 0.12 || crownStart > 0.82) {
            throw new IllegalArgumentException("crownStart outside [0.12,0.82]");
        }
        if (secondaryLeaderStart < 0.35 || secondaryLeaderStart > 0.85) {
            throw new IllegalArgumentException(
                "secondaryLeaderStart outside [0.35,0.85]"
            );
        }
    }

    public static TrunkPlan resolve(
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history
    ) {
        if (quality == null || environment == null || history == null) {
            throw new IllegalArgumentException("trunk planning inputs are required");
        }

        long state = TreeLifeHistory.mix(seed ^ 0x5452554E4B504C41L);
        double variation = TreeLifeHistory.unit(state) - 0.5;
        double baseHeight = switch (quality) {
            case BASIC -> 8.0;
            case MID -> 14.0;
            case HERO -> 24.0;
        };
        double competitionStretch = 1.0
            + environment.forestCompetition() * 0.24;
        double openSpreadPenalty = 1.0
            - environment.openSpaceStrength() * 0.07;
        double stress = environment.slope() * 0.10
            + environment.windStrength() * 0.07
            + history.leaderDamage() * 0.15;
        int height = (int)Math.round(
            baseHeight
                * competitionStretch
                * openSpreadPenalty
                * (1.0 - stress)
                * (0.94 + variation * 0.12)
        );
        height = Math.max(5, Math.min(quality.budget().maxHeight(), height));

        int sections = switch (quality) {
            case BASIC -> 3;
            case MID -> 5;
            case HERO -> 7;
        };
        sections = Math.min(sections, height);

        double radiusBase = switch (quality) {
            case BASIC -> 0.72;
            case MID -> 1.16;
            case HERO -> 1.82;
        };
        double baseRadius = radiusBase
            * (0.82 + history.vigor() * 0.24)
            * (1.0 + environment.openSpaceStrength() * 0.10)
            * (1.0 - environment.forestCompetition() * 0.08);

        double crownStart = 0.20
            + environment.forestCompetition() * 0.23
            + (quality == TreeQualityTier.BASIC ? 0.05 : 0.0)
            + history.crownLoss() * 0.10
            - environment.openSpaceStrength() * 0.08;
        crownStart = clamp(crownStart, 0.16, 0.70);

        double leanScale = height * (0.035 + environment.windStrength() * 0.035);
        double leanX = history.trunkLean() * leanScale
            + environment.windX() * height * 0.025
            + environment.openSpaceX() * height * 0.018;
        double leanZ = environment.windZ() * height * 0.032
            + environment.openSpaceZ() * height * 0.018;

        state = TreeLifeHistory.mix(state);
        double splitJitter = (TreeLifeHistory.unit(state) - 0.5) * 0.08;
        double secondaryLeaderStart = clamp(
            0.58 + history.leaderDamage() * 0.10 + splitJitter,
            0.44,
            0.78
        );

        return new TrunkPlan(
            height,
            sections,
            baseRadius,
            crownStart,
            leanX,
            leanZ,
            history.secondaryLeader(),
            secondaryLeaderStart
        );
    }

    public double radiusAt(double vertical) {
        double t = clamp(vertical, 0.0, 1.0);
        return Math.max(0.18, baseRadius * (1.0 - t * 0.82));
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
