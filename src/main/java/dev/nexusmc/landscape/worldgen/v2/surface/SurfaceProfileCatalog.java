package dev.nexusmc.landscape.worldgen.v2.surface;

import static dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfile.ClimateFamily.*;
import static dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfile.ElevationBand.*;
import static dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfile.SlopeBand.*;
import static dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfile.TerrainFamily.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Complete Stage 6 surface contract for the 53 vanilla 1.21.1 Overworld
 * biomes. Profiles may share geological palettes, but every biome has its own
 * identity and visual-trait contract.
 */
public final class SurfaceProfileCatalog {
    private static final SurfaceProfile.Layers LOAM = layers(
        ids("grass_block", "coarse_dirt"),
        ids("dirt", "rooted_dirt"),
        ids("dirt", "clay", "gravel"),
        ids("stone", "andesite"),
        ids("mud", "clay"),
        ids("gravel", "sand", "clay"),
        ids("sand", "gravel"),
        ids("stone", "andesite", "snow_block")
    );
    private static final SurfaceProfile.Layers FOREST = layers(
        ids("grass_block", "podzol"),
        ids("dirt", "rooted_dirt", "coarse_dirt"),
        ids("dirt", "gravel"),
        ids("stone", "andesite", "mossy_cobblestone"),
        ids("mud", "rooted_dirt", "clay"),
        ids("gravel", "clay"),
        ids("gravel", "sand"),
        ids("stone", "podzol", "snow_block")
    );
    private static final SurfaceProfile.Layers WETLAND_PALETTE = layers(
        ids("mud", "grass_block"),
        ids("mud", "clay", "rooted_dirt"),
        ids("clay", "dirt", "gravel"),
        ids("stone", "mossy_cobblestone"),
        ids("mud", "clay"),
        ids("clay", "mud", "gravel"),
        ids("mud", "sand", "clay"),
        ids("stone", "snow_block")
    );
    private static final SurfaceProfile.Layers ARID_PALETTE = layers(
        ids("sand", "smooth_sandstone"),
        ids("sand", "sandstone"),
        ids("sandstone", "gravel"),
        ids("sandstone", "stone"),
        ids("clay", "mud"),
        ids("sand", "gravel", "clay"),
        ids("sand", "sandstone"),
        ids("stone", "calcite")
    );
    private static final SurfaceProfile.Layers BADLANDS_PALETTE = layers(
        ids("red_sand", "terracotta"),
        ids("orange_terracotta", "terracotta"),
        ids("yellow_terracotta", "red_terracotta", "white_terracotta"),
        ids("red_sandstone", "terracotta"),
        ids("clay", "red_sand"),
        ids("red_sand", "gravel", "clay"),
        ids("red_sand", "red_sandstone"),
        ids("terracotta", "stone")
    );
    private static final SurfaceProfile.Layers ALPINE_LAYERS = layers(
        ids("grass_block", "coarse_dirt"),
        ids("dirt", "gravel"),
        ids("gravel", "stone"),
        ids("stone", "andesite", "calcite"),
        ids("clay", "gravel"),
        ids("gravel", "stone"),
        ids("gravel", "stone"),
        ids("snow_block", "packed_ice", "stone")
    );
    private static final SurfaceProfile.Layers GLACIAL_PALETTE = layers(
        ids("snow_block", "packed_ice"),
        ids("snow_block", "gravel"),
        ids("packed_ice", "stone"),
        ids("stone", "andesite", "blue_ice"),
        ids("ice", "packed_ice"),
        ids("gravel", "clay"),
        ids("gravel", "packed_ice"),
        ids("snow_block", "packed_ice", "blue_ice")
    );
    private static final SurfaceProfile.Layers VOLCANIC_LAYERS = layers(
        ids("tuff", "basalt"),
        ids("tuff", "smooth_basalt"),
        ids("basalt", "blackstone"),
        ids("basalt", "blackstone", "magma_block"),
        ids("clay", "tuff"),
        ids("blackstone", "gravel"),
        ids("blackstone", "gravel"),
        ids("basalt", "snow_block")
    );
    private static final SurfaceProfile.Layers MARINE_SAND = layers(
        ids("sand", "gravel"),
        ids("sand", "sandstone"),
        ids("sandstone", "clay"),
        ids("stone", "sandstone"),
        ids("clay", "mud"),
        ids("sand", "gravel", "clay"),
        ids("sand", "sandstone"),
        ids("stone", "gravel")
    );
    private static final SurfaceProfile.Layers MARINE_ROCK = layers(
        ids("gravel", "stone"),
        ids("gravel", "clay"),
        ids("stone", "tuff"),
        ids("stone", "andesite"),
        ids("clay", "mud"),
        ids("gravel", "clay"),
        ids("gravel", "stone"),
        ids("stone", "packed_ice")
    );
    private static final SurfaceProfile.Layers MYCELIAL_LAYERS = layers(
        ids("mycelium", "podzol"),
        ids("dirt", "rooted_dirt"),
        ids("tuff", "calcite"),
        ids("andesite", "tuff", "calcite"),
        ids("mud", "mycelium"),
        ids("gravel", "clay"),
        ids("gravel", "mycelium"),
        ids("stone", "mycelium")
    );
    private static final SurfaceProfile.Layers CAVE_WET = layers(
        ids("moss_block", "clay"),
        ids("clay", "rooted_dirt"),
        ids("tuff", "stone"),
        ids("stone", "calcite"),
        ids("clay", "moss_block"),
        ids("clay", "gravel"),
        ids("stone"),
        ids("stone")
    );
    private static final SurfaceProfile.Layers CAVE_DRY = layers(
        ids("dripstone_block", "stone"),
        ids("dripstone_block", "tuff"),
        ids("calcite", "tuff"),
        ids("stone", "calcite", "dripstone_block"),
        ids("clay", "dripstone_block"),
        ids("gravel", "clay"),
        ids("stone"),
        ids("stone")
    );

    private static final Map<String, SurfaceProfile> PROFILES = createProfiles();

    private SurfaceProfileCatalog() {
    }

    public static SurfaceProfile require(String biomeId) {
        SurfaceProfile profile = PROFILES.get(biomeId);
        if (profile == null) {
            throw new IllegalArgumentException(
                "No Stage 6 surface profile for " + biomeId
            );
        }
        return profile;
    }

    public static Optional<SurfaceProfile> find(String biomeId) {
        return Optional.ofNullable(PROFILES.get(biomeId));
    }

    /**
     * Climate-aware safe profile for optional or unknown modded biomes.
     */
    public static SurfaceProfile fallback(
        double temperature,
        double humidity,
        boolean underground
    ) {
        if (underground) {
            return humidity > 0.25
                ? require("minecraft:lush_caves")
                : require("minecraft:dripstone_caves");
        }
        if (temperature < -0.35) {
            return require("minecraft:snowy_plains");
        }
        if (temperature > 0.45 && humidity < -0.15) {
            return require("minecraft:desert");
        }
        if (humidity > 0.55) {
            return require("minecraft:swamp");
        }
        if (humidity > 0.18) {
            return require("minecraft:forest");
        }
        return require("minecraft:plains");
    }

    public static Map<String, SurfaceProfile> profiles() {
        return PROFILES;
    }

    private static Map<String, SurfaceProfile> createProfiles() {
        Map<String, SurfaceProfile> result = new LinkedHashMap<>();
        add(result, "plains", TEMPERATE, LOWLAND, LOW, FLAT, LOAM, 4,
            "alluvial_loam", "levee_mosaic", "rock_sparse");
        add(result, "sunflower_plains", SEASONAL_WARM, LOWLAND, MID, GENTLE, LOAM, 4,
            "warm_loess", "dry_terrace", "flower_gap");
        add(result, "snowy_plains", POLAR, LOWLAND, LOW, FLAT, GLACIAL_PALETTE, 3,
            "frozen_loam", "wind_drift", "thermokarst");
        add(result, "ice_spikes", POLAR, GLACIAL, HIGH, STEEP, GLACIAL_PALETTE, 2,
            "moraine", "coherent_ice", "bare_rim");
        add(result, "desert", ARID, DRY_PLATEAU, LOW, ROLLING, ARID_PALETTE, 5,
            "dune_hamada", "sandstone_lens", "playa_sediment");
        add(result, "swamp", WETLAND, LOWLAND, LOW, FLAT, WETLAND_PALETTE, 5,
            "peat_mud", "wet_island", "oxbow_shore");
        add(result, "mangrove_swamp", WETLAND, COAST, LOW, FLAT, WETLAND_PALETTE, 6,
            "tidal_mud", "saline_shore", "lagoon_sediment");
        add(result, "forest", TEMPERATE, OLD_HIGHLAND, MID, ROLLING, FOREST, 4,
            "mixed_loam", "mossy_rock", "canopy_clearing");
        add(result, "flower_forest", TEMPERATE, KARST, MID, GENTLE, LOAM, 4,
            "flower_terrace", "calcareous_pocket", "spring_hollow");
        add(result, "birch_forest", TEMPERATE, OLD_HIGHLAND, MID, GENTLE, LOAM, 3,
            "bright_loam", "pale_stone", "kame_gravel");
        add(result, "dark_forest", TEMPERATE, OLD_HIGHLAND, LOW, ROLLING, FOREST, 5,
            "acidic_soil", "mossy_hollow", "storm_gap");
        add(result, "old_growth_birch_forest", TEMPERATE, OLD_HIGHLAND, HIGH, ROLLING, FOREST, 4,
            "deep_loam", "ravine_rock", "boulder_field");
        add(result, "old_growth_pine_taiga", BOREAL, OLD_HIGHLAND, HIGH, ROLLING, FOREST, 4,
            "podzol_opening", "moraine_gravel", "silicate_boulder");
        add(result, "old_growth_spruce_taiga", BOREAL, OLD_HIGHLAND, HIGH, STEEP, FOREST, 5,
            "wet_podzol", "moss_ravine", "talus_boulder");
        add(result, "taiga", BOREAL, OLD_HIGHLAND, MID, ROLLING, FOREST, 4,
            "podzol_band", "gravel_valley", "lichen_rock");
        add(result, "snowy_taiga", BOREAL, GLACIAL, MID, ROLLING, GLACIAL_PALETTE, 4,
            "snow_podzol", "frozen_gravel", "moraine_hollow");
        add(result, "savanna", SEASONAL_WARM, LOWLAND, MID, ROLLING, LOAM, 3,
            "dry_loam", "seasonal_sediment", "rock_island");
        add(result, "savanna_plateau", SEASONAL_WARM, DRY_PLATEAU, HIGH, STEEP, ARID_PALETTE, 3,
            "plateau_cap", "escarpment", "dry_talus");
        add(result, "windswept_hills", BOREAL, OLD_HIGHLAND, HIGH, STEEP, ALPINE_LAYERS, 2,
            "wind_heath", "bare_ridge", "saddle_scree");
        add(result, "windswept_gravelly_hills", BOREAL, OLD_HIGHLAND, HIGH, CLIFF, ALPINE_LAYERS, 2,
            "gravel_crest", "talus_fan", "stone_gully");
        add(result, "windswept_forest", BOREAL, OLD_HIGHLAND, HIGH, STEEP, FOREST, 3,
            "forest_shoulder", "open_crest", "rock_gate");
        add(result, "windswept_savanna", SEASONAL_WARM, VOLCANIC, HIGH, CLIFF, VOLCANIC_LAYERS, 2,
            "volcanic_neck", "dry_bench", "basalt_talus");
        add(result, "jungle", TROPICAL_HUMID, KARST, MID, STEEP, WETLAND_PALETTE, 5,
            "karst_limestone", "humid_soil", "spring_fan");
        add(result, "sparse_jungle", TROPICAL_HUMID, KARST, MID, ROLLING, LOAM, 4,
            "open_terrace", "limestone_exposure", "seasonal_pocket");
        add(result, "bamboo_jungle", TROPICAL_HUMID, KARST, LOW, GENTLE, WETLAND_PALETTE, 5,
            "alluvial_basin", "sinkhole_clay", "wet_island");
        add(result, "badlands", ARID, DRY_PLATEAU, MID, CLIFF, BADLANDS_PALETTE, 4,
            "painted_bench", "canyon_alluvium", "mesa_cap");
        add(result, "eroded_badlands", ARID, DRY_PLATEAU, HIGH, CLIFF, BADLANDS_PALETTE, 2,
            "hoodoo_layer", "slot_gravel", "talus_apron");
        add(result, "wooded_badlands", SEASONAL_WARM, DRY_PLATEAU, HIGH, STEEP, BADLANDS_PALETTE, 4,
            "wooded_cap", "painted_wall", "spring_lens");
        add(result, "meadow", ALPINE, YOUNG_MOUNTAIN, HIGH, GENTLE, ALPINE_LAYERS, 3,
            "alpine_loam", "flower_terrace", "tarn_gravel");
        add(result, "cherry_grove", TEMPERATE, YOUNG_MOUNTAIN, HIGH, ROLLING, LOAM, 4,
            "sheltered_loam", "petal_clearing", "rock_rim");
        add(result, "grove", ALPINE, GLACIAL, HIGH, STEEP, GLACIAL_PALETTE, 3,
            "subalpine_podzol", "avalanche_gap", "moraine_bowl");
        add(result, "snowy_slopes", ALPINE, GLACIAL, SUMMIT, STEEP, GLACIAL_PALETTE, 2,
            "snow_belt", "glacial_gully", "moraine_apron");
        add(result, "frozen_peaks", POLAR, GLACIAL, SUMMIT, CLIFF, GLACIAL_PALETTE, 2,
            "summit_ice", "blue_ice", "crystalline_exposure");
        add(result, "jagged_peaks", ALPINE, YOUNG_MOUNTAIN, SUMMIT, CLIFF, ALPINE_LAYERS, 1,
            "arete", "couloir", "bare_summit");
        add(result, "stony_peaks", ALPINE, KARST, SUMMIT, CLIFF, ALPINE_LAYERS, 1,
            "calcite_shelf", "warm_rock", "spring_crack");
        add(result, "river", WETLAND, RIVER_CORRIDOR, LOW, FLAT, WETLAND_PALETTE, 5,
            "ordered_channel", "point_bar", "floodplain_transition");
        add(result, "frozen_river", POLAR, RIVER_CORRIDOR, LOW, FLAT, GLACIAL_PALETTE, 4,
            "ice_channel", "frozen_bar", "snowmelt_bank");
        add(result, "beach", MARINE, COAST, LOW, GENTLE, MARINE_SAND, 5,
            "swash_berm", "dune_transition", "storm_sediment");
        add(result, "snowy_beach", POLAR, COAST, LOW, GENTLE, GLACIAL_PALETTE, 3,
            "snow_berm", "pressure_ice", "gravel_pocket");
        add(result, "stony_shore", MARINE, COAST, MID, CLIFF, MARINE_ROCK, 2,
            "wave_platform", "cobble_fan", "wet_crack");
        add(result, "warm_ocean", MARINE, OCEAN_SHELF, SEA_FLOOR, SUBMERGED, MARINE_SAND, 4,
            "carbonate_shelf", "reef_sediment", "lagoon_floor");
        add(result, "lukewarm_ocean", MARINE, OCEAN_SHELF, SEA_FLOOR, SUBMERGED, MARINE_SAND, 4,
            "sand_wave", "seagrass_basin", "patch_reef");
        add(result, "deep_lukewarm_ocean", MARINE, OCEAN_DEEP, SEA_FLOOR, SUBMERGED, MARINE_ROCK, 3,
            "shelf_slope", "submarine_canyon", "isolated_bank");
        add(result, "ocean", MARINE, OCEAN_SHELF, SEA_FLOOR, SUBMERGED, MARINE_ROCK, 3,
            "mixed_shelf", "rocky_bank", "sediment_basin");
        add(result, "deep_ocean", MARINE, OCEAN_DEEP, SEA_FLOOR, SUBMERGED, MARINE_ROCK, 2,
            "abyssal_plain", "seamount_rock", "deep_sediment");
        add(result, "cold_ocean", MARINE, OCEAN_SHELF, SEA_FLOOR, SUBMERGED, MARINE_ROCK, 3,
            "glacial_shelf", "drowned_moraine", "kelp_gravel");
        add(result, "deep_cold_ocean", MARINE, OCEAN_DEEP, SEA_FLOOR, SUBMERGED, MARINE_ROCK, 2,
            "glacial_trough", "deep_terrace", "cold_sediment");
        add(result, "frozen_ocean", POLAR, OCEAN_SHELF, SEA_FLOOR, SUBMERGED, GLACIAL_PALETTE, 3,
            "pack_ice", "lead_margin", "pressure_ridge");
        add(result, "deep_frozen_ocean", POLAR, OCEAN_DEEP, SEA_FLOOR, SUBMERGED, GLACIAL_PALETTE, 2,
            "ice_shelf", "deep_trough", "tabular_ice");
        add(result, "mushroom_fields", MYCELIAL, OLD_HIGHLAND, MID, ROLLING, MYCELIAL_LAYERS, 4,
            "mycelial_plateau", "fungal_wet_pocket", "weathered_craton");
        add(result, "dripstone_caves", UNDERGROUND, CAVE, SUBTERRANEAN, INTERNAL, CAVE_DRY, 3,
            "carbonate_floor", "drip_gradient", "underground_channel");
        add(result, "lush_caves", UNDERGROUND, CAVE, SUBTERRANEAN, INTERNAL, CAVE_WET, 4,
            "moss_terrace", "clay_pool", "root_shaft");
        add(result, "deep_dark", UNDERGROUND, CAVE, SUBTERRANEAN, INTERNAL, CAVE_DRY, 2,
            "sculk_threshold", "rift_shelf", "bare_abyss");
        if (result.size() != 53) {
            throw new IllegalStateException(
                "Expected 53 Overworld profiles, got " + result.size()
            );
        }
        return Map.copyOf(result);
    }

    private static void add(
        Map<String, SurfaceProfile> profiles,
        String path,
        SurfaceProfile.ClimateFamily climate,
        SurfaceProfile.TerrainFamily terrain,
        SurfaceProfile.ElevationBand elevation,
        SurfaceProfile.SlopeBand slope,
        SurfaceProfile.Layers layers,
        int depth,
        String... traits
    ) {
        String biomeId = "minecraft:" + path;
        SurfaceProfile previous = profiles.put(
            biomeId,
            new SurfaceProfile(
                biomeId,
                "nexus_landscape:surface/" + path,
                climate,
                terrain,
                elevation,
                slope,
                layers,
                depth,
                Set.of(traits)
            )
        );
        if (previous != null) {
            throw new IllegalStateException("Duplicate profile: " + biomeId);
        }
    }

    private static SurfaceProfile.Layers layers(
        List<String> top,
        List<String> soil,
        List<String> transition,
        List<String> exposedRock,
        List<String> wet,
        List<String> sediment,
        List<String> coast,
        List<String> alpine
    ) {
        return new SurfaceProfile.Layers(
            top,
            soil,
            transition,
            exposedRock,
            wet,
            sediment,
            coast,
            alpine
        );
    }

    private static List<String> ids(String... paths) {
        return java.util.Arrays.stream(paths)
            .map(path -> "minecraft:" + path)
            .toList();
    }
}
