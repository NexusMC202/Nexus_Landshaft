package dev.nexusmc.landscape.worldgen.v2.field;

import dev.nexusmc.landscape.NexusLandscape;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class NexusV2Noises {
    public static final ResourceKey<NormalNoise.NoiseParameters> CLIMATE_TEMPERATURE =
        key("climate_temperature");
    public static final ResourceKey<NormalNoise.NoiseParameters> CLIMATE_HUMIDITY =
        key("climate_humidity");
    public static final ResourceKey<NormalNoise.NoiseParameters> WARP_X =
        key("v2/province_warp_x");
    public static final ResourceKey<NormalNoise.NoiseParameters> WARP_Z =
        key("v2/province_warp_z");
    public static final ResourceKey<NormalNoise.NoiseParameters> PROVINCE_MACRO =
        key("v2/province_macro");
    public static final ResourceKey<NormalNoise.NoiseParameters> PROVINCE_DETAIL =
        key("v2/province_detail");
    public static final ResourceKey<NormalNoise.NoiseParameters> PROVINCE_RIDGE =
        key("v2/province_ridge");
    public static final ResourceKey<NormalNoise.NoiseParameters> VOLCANIC_ARC =
        key("v2/volcanic_arc");
    public static final ResourceKey<NormalNoise.NoiseParameters> COMPOSITION =
        key("v2/composition");
    public static final ResourceKey<NormalNoise.NoiseParameters> HYDROLOGY_LAYOUT =
        key("v2/hydrology_layout");
    public static final ResourceKey<NormalNoise.NoiseParameters> HYDROLOGY_TRIBUTARY =
        key("v2/hydrology_tributary");
    public static final ResourceKey<NormalNoise.NoiseParameters> HYDROLOGY_ELEVATION =
        key("v2/hydrology_elevation");

    private NexusV2Noises() {
    }

    private static ResourceKey<NormalNoise.NoiseParameters> key(String path) {
        return ResourceKey.create(
            Registries.NOISE,
            ResourceLocation.fromNamespaceAndPath(NexusLandscape.MOD_ID, path)
        );
    }
}
