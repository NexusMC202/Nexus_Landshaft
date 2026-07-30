package dev.nexusmc.landscape.worldgen.v2.surface;

import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;

/**
 * Shared context composition for surface, vegetation and diagnostics.
 */
public final class SurfaceContextFactory {
    private SurfaceContextFactory() {
    }

    public static SurfaceContext create(
        long seed,
        int worldX,
        int worldZ,
        int surfaceY,
        String biomeKey,
        NexusV2FieldSampler.SurfaceInputs input,
        RegionalFieldMath.Sample regional,
        HydrologyMath.Sample river,
        HydrologyMath.BasinSample basin,
        double slope
    ) {
        double coast = channel(input, RegionalFieldMath.Channel.COAST_WEIGHT);
        double volcanic = Math.max(
            regional.provinceWeight(RegionalFieldMath.Province.VOLCANIC_BELT),
            channel(input, RegionalFieldMath.Channel.VOLCANIC_MOUNTAINS)
        );
        double glacier = channel(input, RegionalFieldMath.Channel.GLACIER_MASS);
        double groundwater = clamp01(
            (input.humidity() + 1.0) * 0.34
                + regional.provinceWeight(
                    RegionalFieldMath.Province.WETLAND_BASIN
                ) * 0.48
                + basin.mask() * 0.25
        );
        return new SurfaceContext(
            seed,
            worldX,
            worldZ,
            surfaceY,
            biomeKey,
            regional.dominantProvince().serializedName(),
            input.temperature(),
            input.humidity(),
            input.continentalness(),
            input.erosion(),
            input.weirdness(),
            surfaceY,
            clamp01((surfaceY + 64.0) / 384.0),
            slope,
            river.distance(),
            river.mask(),
            clamp01(river.mask() * 0.72
                + smoothstep(42.0, 4.0, river.distance()) * 0.28),
            basin.mask(),
            coast,
            192.0 * (1.0 - coast),
            groundwater,
            volcanic,
            glacier,
            clamp01((surfaceY - 128.0) / 96.0 + glacier * 0.55),
            channel(input, RegionalFieldMath.Channel.CANYON_INCISION),
            regional.provinceWeight(RegionalFieldMath.Province.KARST_BELT),
            regional.provinceWeight(RegionalFieldMath.Province.MYCELIAL_CRATON),
            clamp01(
                regional.provinceWeight(
                    RegionalFieldMath.Province.OCEANIC_CRUST
                ) * (0.35 + coast * 0.65)
            ),
            SurfaceNoise.value(seed, worldX, worldZ, 48, 0x51FACEL),
            SurfaceNoise.value(seed, worldX, worldZ, 11, 0x6D47E21L)
        );
    }

    public static RegionalFieldMath.Sample regional(
        NexusV2FieldSampler.SurfaceInputs input
    ) {
        return RegionalFieldMath.sample(
            input.continentalness(),
            input.temperature(),
            input.humidity(),
            input.macro(),
            input.detail(),
            input.ridge(),
            input.volcanic(),
            input.composition()
        );
    }

    public static double channel(
        NexusV2FieldSampler.SurfaceInputs input,
        RegionalFieldMath.Channel channel
    ) {
        return RegionalFieldMath.compute(
            channel,
            input.continentalness(),
            input.temperature(),
            input.humidity(),
            input.macro(),
            input.detail(),
            input.ridge(),
            input.volcanic(),
            input.composition()
        );
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 < edge0) {
            return 1.0 - smoothstep(edge1, edge0, value);
        }
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
