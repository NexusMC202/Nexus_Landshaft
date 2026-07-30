package dev.nexusmc.landscape.worldgen.v2.surface;

/**
 * Normalized inputs for surface selection. It contains no world objects and is
 * therefore safe to sample in any query order and on worldgen workers.
 */
public record SurfaceContext(
    double temperature,
    double humidity,
    double continentalness,
    double erosion,
    double weirdness,
    double elevation,
    double slope,
    double riverDistance,
    double riverMask,
    double lakeBasinMask,
    double coastWeight,
    double groundwater,
    double volcanicWeight,
    double glacierWeight,
    double canyonWeight,
    double karstWeight,
    double mycelialWeight,
    double archipelagoWeight
) {
    public SurfaceContext {
        requireFinite(temperature, "temperature");
        requireFinite(humidity, "humidity");
        requireFinite(continentalness, "continentalness");
        requireFinite(erosion, "erosion");
        requireFinite(weirdness, "weirdness");
        requireFinite(elevation, "elevation");
        requireFinite(slope, "slope");
        requireFinite(riverDistance, "riverDistance");
        riverMask = unit(riverMask, "riverMask");
        lakeBasinMask = unit(lakeBasinMask, "lakeBasinMask");
        coastWeight = unit(coastWeight, "coastWeight");
        groundwater = unit(groundwater, "groundwater");
        volcanicWeight = unit(volcanicWeight, "volcanicWeight");
        glacierWeight = unit(glacierWeight, "glacierWeight");
        canyonWeight = unit(canyonWeight, "canyonWeight");
        karstWeight = unit(karstWeight, "karstWeight");
        mycelialWeight = unit(mycelialWeight, "mycelialWeight");
        archipelagoWeight = unit(archipelagoWeight, "archipelagoWeight");
        if (slope < 0.0) {
            throw new IllegalArgumentException("slope cannot be negative");
        }
    }

    public boolean activeChannel() {
        return riverMask >= 0.50;
    }

    public boolean wetBank() {
        return !activeChannel()
            && (riverDistance <= 18.0 || groundwater >= 0.72);
    }

    public boolean exposedSlope() {
        return slope >= 0.42;
    }

    public boolean alpineExposure() {
        return elevation >= 150.0 || glacierWeight >= 0.55;
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
