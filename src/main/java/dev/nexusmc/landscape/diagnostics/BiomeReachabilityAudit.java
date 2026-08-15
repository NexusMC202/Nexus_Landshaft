package dev.nexusmc.landscape.diagnostics;

import com.mojang.datafixers.util.Pair;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfile;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfileCatalog;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

/** Samples the real vanilla Overworld parameter router on a semantic grid. */
public final class BiomeReachabilityAudit {
    private static final float[] TEMPERATURE =
        {-1.0F, -0.75F, -0.5F, -0.25F, 0.0F, 0.35F, 0.7F, 1.0F};
    private static final float[] HUMIDITY =
        {-1.0F, -0.7F, -0.4F, -0.1F, 0.2F, 0.5F, 0.8F};
    private static final float[] CONTINENTALNESS =
        {-1.1F, -0.85F, -0.55F, -0.3F, -0.1F, 0.1F, 0.35F, 0.7F, 1.0F};
    private static final float[] EROSION =
        {-1.0F, -0.75F, -0.5F, -0.25F, 0.0F, 0.3F, 0.55F, 0.8F, 1.0F};
    private static final float[] DEPTH = {0.0F, 0.5F, 1.0F};
    private static final float[] WEIRDNESS =
        {-1.0F, -0.8F, -0.6F, -0.4F, -0.2F, 0.0F,
            0.2F, 0.4F, 0.6F, 0.8F, 1.0F};

    private BiomeReachabilityAudit() {
    }

    public static void main(String[] args) throws Exception {
        Climate.ParameterList<ResourceKey<Biome>> router = overworldRouter();
        Map<SurfaceProfile.ClimateFamily, Integer> routed =
            new EnumMap<>(SurfaceProfile.ClimateFamily.class);
        Set<String> routedBiomes = new LinkedHashSet<>();
        Set<String> caveBiomes = new LinkedHashSet<>();
        Set<String> unexpectedFallback = new LinkedHashSet<>();

        for (float temperature : TEMPERATURE) {
            for (float humidity : HUMIDITY) {
                for (float continentalness : CONTINENTALNESS) {
                    for (float erosion : EROSION) {
                        for (float depth : DEPTH) {
                            for (float weirdness : WEIRDNESS) {
                                ResourceKey<Biome> key = router.findValue(
                                    Climate.target(
                                        temperature,
                                        humidity,
                                        continentalness,
                                        erosion,
                                        depth,
                                        weirdness
                                    )
                                );
                                String id = key.location().toString();
                                SurfaceProfile profile =
                                    SurfaceProfileCatalog.find(id).orElse(null);
                                if (profile == null) {
                                    unexpectedFallback.add(id);
                                    continue;
                                }
                                if (profile.climate()
                                    == SurfaceProfile.ClimateFamily.UNDERGROUND) {
                                    caveBiomes.add(id);
                                } else {
                                    routedBiomes.add(id);
                                    routed.merge(profile.climate(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
        }

        EnumSet<SurfaceProfile.ClimateFamily> expected =
            EnumSet.allOf(SurfaceProfile.ClimateFamily.class);
        expected.remove(SurfaceProfile.ClimateFamily.UNDERGROUND);
        EnumSet<SurfaceProfile.ClimateFamily> unreachable = expected.clone();
        unreachable.removeAll(routed.keySet());

        require(unexpectedFallback.isEmpty(),
            "normal router requires fallback profiles: " + unexpectedFallback);
        require(unreachable.isEmpty(),
            "unreachable terrestrial climate families: " + unreachable);
        require(!caveBiomes.isEmpty(), "cave-only routing was not observed");

        System.out.println("BiomeReachabilityAudit:");
        for (SurfaceProfile.ClimateFamily family : expected) {
            System.out.println("  " + family + " = "
                + routed.getOrDefault(family, 0));
        }
        System.out.println("  routed_biomes = " + routedBiomes.size());
        System.out.println("  cave_only = " + caveBiomes);
        System.out.println("  unexpected_fallback = " + unexpectedFallback);
        System.out.println("  unreachable = " + unreachable);
    }

    private static Climate.ParameterList<ResourceKey<Biome>> overworldRouter()
        throws Exception {
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> values =
            new ArrayList<>();
        Method addBiomes = OverworldBiomeBuilder.class.getDeclaredMethod(
            "addBiomes",
            Consumer.class
        );
        addBiomes.setAccessible(true);
        Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> collector =
            values::add;
        addBiomes.invoke(new OverworldBiomeBuilder(), collector);
        require(!values.isEmpty(), "vanilla Overworld router is empty");
        return new Climate.ParameterList<>(List.copyOf(values));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}


