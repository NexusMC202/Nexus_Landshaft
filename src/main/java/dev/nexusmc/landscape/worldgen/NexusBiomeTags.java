package dev.nexusmc.landscape.worldgen;

import dev.nexusmc.landscape.NexusLandscape;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class NexusBiomeTags {
    public static final TagKey<Biome> HUMID_KARST_REGION = create("humid_karst_region");
    public static final TagKey<Biome> VOLCANIC_PROVINCES = create("volcanic_provinces");
    public static final TagKey<Biome> MYCELIAL_REACHES = create("mycelial_reaches");
    public static final TagKey<Biome> HAS_DEEP_DARK_RIFTS = create("has_deep_dark_rifts");

    private NexusBiomeTags() {
    }

    private static TagKey<Biome> create(String path) {
        return TagKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(NexusLandscape.MOD_ID, path)
        );
    }
}
