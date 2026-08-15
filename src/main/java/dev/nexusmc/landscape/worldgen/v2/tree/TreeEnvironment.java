package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Normalized environmental inputs used by tree growth. This model is kept
 * independent from Minecraft classes so generation can be tested off-thread.
 */
public record TreeEnvironment(
    double slope,
    double soilMoisture,
    double forestCompetition,
    double riverInfluence,
    double windX,
    double windZ,
    double openSpaceX,
    double openSpaceZ,
    int surfaceY
) {
    public TreeEnvironment {
        slope = unit(slope, "slope");
        soilMoisture = unit(soilMoisture, "soilMoisture");
        forestCompetition = unit(forestCompetition, "forestCompetition");
        riverInfluence = unit(riverInfluence, "riverInfluence");
        windX = signed(windX, "windX");
        windZ = signed(windZ, "windZ");
        openSpaceX = signed(openSpaceX, "openSpaceX");
        openSpaceZ = signed(openSpaceZ, "openSpaceZ");
    }

    public double windStrength() {
        return Math.min(1.0, Math.hypot(windX, windZ));
    }

    public double openSpaceStrength() {
        return Math.min(1.0, Math.hypot(openSpaceX, openSpaceZ));
    }

    private static double unit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " outside [0,1]: " + value);
        }
        return value;
    }

    private static double signed(double value, String name) {
        if (!Double.isFinite(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " outside [-1,1]: " + value);
        }
        return value;
    }
}
