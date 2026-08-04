package dev.nexusmc.landscape.worldgen.v2.surface;

/**
 * Normalized inputs for surface selection. It contains no world objects and is
 * therefore safe to sample in any query order and on worldgen workers.
 */
public record SurfaceContext(
    long worldSeed,
    int blockX,
    int blockZ,
    int surfaceY,
    String biomeKey,
    String terrainProvince,
    double temperature,
    double humidity,
    double continentalness,
    double erosion,
    double weirdness,
    double elevation,
    double normalizedHeight,
    double slope,
    double riverDistance,
    double riverMask,
    double riverInfluence,
    double lakeBasinMask,
    double coastWeight,
    double oceanDistance,
    double groundwater,
    double volcanicWeight,
    double glacierWeight,
    double alpineInfluence,
    double canyonWeight,
    double karstWeight,
    double mycelialWeight,
    double archipelagoWeight,
    double localNoise,
    double materialNoise
) {
    public SurfaceContext {
        if (biomeKey == null || terrainProvince == null) {
            throw new IllegalArgumentException("biome/province cannot be null");
        }
        requireFinite(temperature, "temperature");
        requireFinite(humidity, "humidity");
        requireFinite(continentalness, "continentalness");
        requireFinite(erosion, "erosion");
        requireFinite(weirdness, "weirdness");
        requireFinite(elevation, "elevation");
        normalizedHeight = unit(normalizedHeight, "normalizedHeight");
        slope = unit(slope, "slope");
        requireFinite(riverDistance, "riverDistance");
        riverMask = unit(riverMask, "riverMask");
        riverInfluence = unit(riverInfluence, "riverInfluence");
        lakeBasinMask = unit(lakeBasinMask, "lakeBasinMask");
        coastWeight = unit(coastWeight, "coastWeight");
        requireFinite(oceanDistance, "oceanDistance");
        groundwater = unit(groundwater, "groundwater");
        volcanicWeight = unit(volcanicWeight, "volcanicWeight");
        glacierWeight = unit(glacierWeight, "glacierWeight");
        alpineInfluence = unit(alpineInfluence, "alpineInfluence");
        canyonWeight = unit(canyonWeight, "canyonWeight");
        karstWeight = unit(karstWeight, "karstWeight");
        mycelialWeight = unit(mycelialWeight, "mycelialWeight");
        archipelagoWeight = unit(archipelagoWeight, "archipelagoWeight");
        localNoise = unit(localNoise, "localNoise");
        materialNoise = unit(materialNoise, "materialNoise");
    }

    public boolean activeChannel() {
        return riverMask >= 0.50;
    }

    public boolean wetBank() {
        return !activeChannel()
            && (riverDistance <= 18.0 || groundwater >= 0.72);
    }

    public boolean exposedSlope() {
        return slope >= 0.16;
    }

    public boolean alpineExposure() {
        return alpineInfluence >= 0.5
            || elevation >= 150.0
            || glacierWeight >= 0.55;
    }

    private static double unit(double value, String description) {
        requireFinite(value, description);
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                description + " outside [0, 1]: " + value
            );
        }
        return value;
    }

    private static void requireFinite(double value, String description) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                description + " must be finite: " + value
            );
        }
    }
}
