package dev.nexusmc.landscape.worldgen.v2.terrain;

import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;

/**
 * One analytical terrain envelope shared by density, hydrology and
 * diagnostics. It deliberately has no world/chunk access.
 */
public final class AnalyticalTerrainMath {
    public static final double DENSITY_VERTICAL_SCALE = 48.0;

    private AnalyticalTerrainMath() {
    }

    public static Sample sample(
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        double land = smoothstep(-0.42, -0.04, continentalness);
        double continentalShelf = lerp(
            34.0 + continentalness * 24.0,
            67.0 + smoothstep(-0.18, 0.72, continentalness) * 45.0,
            land
        );
        double provinceUplift = RegionalFieldMath.compute(
            RegionalFieldMath.Channel.MACRO_UPLIFT,
            continentalness,
            temperature,
            humidity,
            macro,
            detail,
            ridge,
            volcanic,
            composition
        ) * 58.0;
        double landformOffset = RegionalFieldMath.compute(
            RegionalFieldMath.Channel.LANDFORM_OFFSET,
            continentalness,
            temperature,
            humidity,
            macro,
            detail,
            ridge,
            volcanic,
            composition
        ) * 82.0;
        double valleyAxis = 1.0 - smoothstep(0.04, 0.34, Math.abs(detail));
        double broadValley = land * valleyAxis
            * (0.46 + 0.54 * smoothstep(-0.35, 0.52, humidity))
            * 24.0;
        double canyonIncision = RegionalFieldMath.compute(
            RegionalFieldMath.Channel.CANYON_INCISION,
            continentalness,
            temperature,
            humidity,
            macro,
            detail,
            ridge,
            volcanic,
            composition
        ) * 34.0;
        double glacierMass = RegionalFieldMath.compute(
            RegionalFieldMath.Channel.GLACIER_MASS,
            continentalness,
            temperature,
            humidity,
            macro,
            detail,
            ridge,
            volcanic,
            composition
        );
        double glacierCarve = glacierMass
            * (0.35 + 0.65 * valleyAxis)
            * 22.0;
        double regionalErosion = clamp(
            composition * 7.0 - Math.abs(detail) * 3.5 - Math.max(0.0, humidity) * 2.0,
            -10.0,
            10.0
        );
        double surfaceY = clamp(
            continentalShelf
                + provinceUplift
                + landformOffset
                - broadValley
                - canyonIncision
                - glacierCarve
                + regionalErosion,
            -48.0,
            304.0
        );
        return new Sample(
            surfaceY,
            continentalShelf,
            provinceUplift,
            landformOffset,
            broadValley,
            canyonIncision,
            glacierCarve,
            regionalErosion,
            glacierMass
        );
    }

    public static double density(double surfaceY, int blockY) {
        return clamp((surfaceY - blockY) / DENSITY_VERTICAL_SCALE, -1.5, 1.5);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Sample(
        double surfaceY,
        double continentBaseY,
        double provinceUpliftY,
        double landformOffsetY,
        double broadValleyY,
        double canyonIncisionY,
        double glacierCarveY,
        double regionalErosionY,
        double glacierMass
    ) {
    }
}
