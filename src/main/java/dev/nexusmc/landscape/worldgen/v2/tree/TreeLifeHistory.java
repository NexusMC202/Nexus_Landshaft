package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Deterministic biography used to turn a species profile into a tree with a
 * readable history instead of uniform random branching.
 */
public record TreeLifeHistory(
    int ageYears,
    double vigor,
    double crownLoss,
    double trunkLean,
    double leaderDamage,
    double deadBranchShare,
    boolean secondaryLeader
) {
    public TreeLifeHistory {
        if (ageYears < 1 || ageYears > 2_000) {
            throw new IllegalArgumentException("ageYears outside supported range");
        }
        checkUnit(vigor, "vigor");
        checkUnit(crownLoss, "crownLoss");
        checkSigned(trunkLean, "trunkLean");
        checkUnit(leaderDamage, "leaderDamage");
        checkUnit(deadBranchShare, "deadBranchShare");
    }

    public static TreeLifeHistory generate(
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        long state = mix(seed ^ 0x54A9B6D10F2E3C47L);
        double ageRoll = unit(state);
        state = mix(state);
        double damageRoll = unit(state);
        state = mix(state);
        double asymmetryRoll = unit(state);
        state = mix(state);
        double mortalityRoll = unit(state);

        int baseAge = switch (quality) {
            case BASIC -> 18;
            case MID -> 42;
            case HERO -> 90;
        };
        int ageSpan = switch (quality) {
            case BASIC -> 32;
            case MID -> 78;
            case HERO -> 180;
        };
        int age = baseAge + (int)Math.floor(ageRoll * ageSpan);

        double stress = clamp01(
            environment.slope() * 0.28
                + environment.windStrength() * 0.32
                + environment.competition() * 0.22
                + (1.0 - environment.soilMoisture()) * 0.18
        );
        double vigor = clamp01(
            0.86
                + environment.soilMoisture() * 0.18
                - environment.competition() * 0.24
                - stress * 0.20
        );
        double crownLoss = clamp01(stress * (0.18 + damageRoll * 0.38));
        double leaderDamage = clamp01(
            Math.max(0.0, environment.windStrength() - 0.45)
                * (0.30 + damageRoll * 0.65)
        );
        double deadBranches = clamp01(
            stress * 0.42 + mortalityRoll * 0.14
        );
        double leanSign = environment.windX() == 0.0
            ? (asymmetryRoll < 0.5 ? -1.0 : 1.0)
            : Math.signum(environment.windX());
        double lean = clampSigned(
            leanSign * environment.windStrength()
                * (0.12 + asymmetryRoll * 0.28)
        );
        boolean secondaryLeader = quality != TreeQualityTier.BASIC
            && leaderDamage > 0.22
            && unit(mix(state)) > 0.42;

        return new TreeLifeHistory(
            age,
            vigor,
            crownLoss,
            lean,
            leaderDamage,
            deadBranches,
            secondaryLeader
        );
    }

    private static void checkUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " outside [0,1]");
        }
    }

    private static void checkSigned(double value, String name) {
        if (!Double.isFinite(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " outside [-1,1]");
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
