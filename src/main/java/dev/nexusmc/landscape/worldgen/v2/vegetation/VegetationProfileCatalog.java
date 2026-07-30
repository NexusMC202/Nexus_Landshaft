package dev.nexusmc.landscape.worldgen.v2.vegetation;

import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfileCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One working vegetation profile for every Stage 6 surface profile.
 */
public final class VegetationProfileCatalog {
    private static final Set<String> OCEANS = Set.of(
        "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean",
        "deep_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean",
        "deep_frozen_ocean"
    );
    private static final Set<String> CAVES = Set.of(
        "dripstone_caves", "lush_caves", "deep_dark"
    );
    private static final Map<String, VegetationProfile> PROFILES = create();

    private VegetationProfileCatalog() {
    }

    public static Map<String, VegetationProfile> profiles() {
        return PROFILES;
    }

    public static Optional<VegetationProfile> find(String biomeId) {
        return Optional.ofNullable(PROFILES.get(biomeId));
    }

    public static VegetationProfile require(String biomeId) {
        VegetationProfile profile = PROFILES.get(biomeId);
        if (profile == null) {
            throw new IllegalArgumentException("No vegetation profile for " + biomeId);
        }
        return profile;
    }

    public static VegetationProfile fallback(
        double temperature,
        double humidity,
        boolean underground
    ) {
        String surface = SurfaceProfileCatalog.fallback(
            temperature,
            humidity,
            underground
        ).biomeId();
        return require(surface);
    }

    private static Map<String, VegetationProfile> create() {
        Map<String, VegetationProfile> result = new LinkedHashMap<>();
        for (String biomeId : SurfaceProfileCatalog.profiles().keySet()) {
            String path = biomeId.substring("minecraft:".length());
            result.put(biomeId, profile(biomeId, path));
        }
        if (result.size() != 53) {
            throw new IllegalStateException(
                "Expected 53 vegetation profiles, got " + result.size()
            );
        }
        return Map.copyOf(result);
    }

    private static VegetationProfile profile(String biomeId, String path) {
        if (OCEANS.contains(path)) {
            return create(
                biomeId,
                VegetationProfile.Family.AQUATIC,
                0, 0, 0, 0, 0, 0.03, 0, 0.75, 0.0, 64,
                List.of(),
                Set.of("minecraft:seagrass", "minecraft:kelp"),
                false,
                "submerged aquatic biome; terrestrial grammar disabled"
            );
        }
        if (CAVES.contains(path)) {
            VegetationProfile.Family family = switch (path) {
                case "lush_caves" -> VegetationProfile.Family.LUSH_CAVE;
                case "deep_dark" -> VegetationProfile.Family.DEEP_DARK;
                default -> VegetationProfile.Family.DRIPSTONE_CAVE;
            };
            return create(
                biomeId, family, 0, 0, 0, 0, 0, 0, 0, 1, 0, 40,
                List.of(),
                Set.of(path.equals("lush_caves")
                    ? "minecraft:moss_carpet"
                    : path.equals("deep_dark")
                        ? "minecraft:sculk_vein"
                        : "minecraft:pointed_dripstone"),
                false,
                "underground grammar handled by cave decoration pass"
            );
        }
        return switch (path) {
            case "forest", "flower_forest" -> create(biomeId,
                VegetationProfile.Family.TEMPERATE_FOREST,
                0.58, 0.48, 0.70, path.equals("flower_forest") ? 0.58 : 0.16,
                0.12, 0.08, 0.035, 0.18, 0.62, 154,
                List.of(VegetationProfile.TreeShape.OAK_ROUNDED,
                    VegetationProfile.TreeShape.BIRCH_COLUMN),
                Set.of("minecraft:short_grass", "minecraft:fern"), true, "");
            case "birch_forest", "old_growth_birch_forest" -> create(biomeId,
                VegetationProfile.Family.TEMPERATE_FOREST,
                path.startsWith("old") ? 0.68 : 0.52, 0.36, 0.62, 0.10,
                0.15, 0.12, path.startsWith("old") ? 0.08 : 0.02,
                0.20, 0.58, 164,
                List.of(VegetationProfile.TreeShape.BIRCH_COLUMN),
                Set.of("minecraft:fern", "minecraft:short_grass"), true, "");
            case "dark_forest" -> create(biomeId,
                VegetationProfile.Family.TEMPERATE_FOREST,
                0.82, 0.64, 0.50, 0.04, 0.28, 0.10, 0.06,
                0.12, 0.55, 148,
                List.of(VegetationProfile.TreeShape.DARK_OAK_BROAD),
                Set.of("minecraft:brown_mushroom", "minecraft:fern"), true, "");
            case "taiga", "snowy_taiga", "old_growth_pine_taiga",
                 "old_growth_spruce_taiga", "grove" -> create(biomeId,
                VegetationProfile.Family.BOREAL_FOREST,
                path.contains("old_growth") ? 0.72 : 0.56,
                0.44, 0.56, 0.03, 0.22, 0.18,
                path.contains("old_growth") ? 0.09 : 0.025,
                path.equals("grove") ? 0.30 : 0.17, 0.54,
                path.equals("grove") ? 142 : 168,
                List.of(path.contains("pine")
                    ? VegetationProfile.TreeShape.PINE_TALL
                    : VegetationProfile.TreeShape.SPRUCE_CONICAL),
                Set.of("minecraft:fern", "minecraft:sweet_berry_bush"), true, "");
            case "jungle", "sparse_jungle", "bamboo_jungle" -> create(biomeId,
                VegetationProfile.Family.TROPICAL_HUMID,
                path.equals("sparse_jungle") ? 0.36 : 0.78,
                0.72, 0.84, 0.12, 0.20, 0.06, 0.055,
                path.equals("sparse_jungle") ? 0.32 : 0.10, 0.64, 184,
                List.of(VegetationProfile.TreeShape.JUNGLE_EMERGENT),
                Set.of("minecraft:fern", "minecraft:large_fern"), true, "");
            case "swamp", "mangrove_swamp", "river" -> create(biomeId,
                VegetationProfile.Family.WETLAND,
                path.equals("river") ? 0.20 : 0.62,
                0.70, 0.78, 0.12, 0.30, 0.04, 0.025,
                0.26, 0.32, 132,
                List.of(path.equals("mangrove_swamp")
                    ? VegetationProfile.TreeShape.MANGROVE_ROOTED
                    : VegetationProfile.TreeShape.OAK_ROUNDED),
                Set.of("minecraft:fern", "minecraft:short_grass"), true, "");
            case "savanna", "savanna_plateau", "windswept_savanna" -> create(biomeId,
                VegetationProfile.Family.SAVANNA,
                0.24, 0.28, 0.66, 0.05, 0.05, 0.16, 0.018,
                0.42, 0.50, 172,
                List.of(VegetationProfile.TreeShape.ACACIA_FLAT),
                Set.of("minecraft:short_grass", "minecraft:tall_grass"), true, "");
            case "desert", "badlands", "eroded_badlands", "wooded_badlands" ->
                create(biomeId, VegetationProfile.Family.ARID,
                    path.equals("wooded_badlands") ? 0.22 : 0.0,
                    0.10, 0.18, 0.01, 0.01, 0.30, 0.005,
                    0.62, 0.48, 180,
                    path.equals("wooded_badlands")
                        ? List.of(VegetationProfile.TreeShape.OAK_ROUNDED)
                        : List.of(),
                    Set.of("minecraft:dead_bush", "minecraft:short_dry_grass"),
                    true, "");
            case "meadow", "plains", "sunflower_plains" -> create(biomeId,
                VegetationProfile.Family.MEADOW,
                0.08, 0.18, 0.76,
                path.equals("sunflower_plains") ? 0.50 : 0.30,
                0.03, 0.05, 0.008, 0.58, 0.46, 158,
                List.of(VegetationProfile.TreeShape.OAK_ROUNDED),
                Set.of("minecraft:short_grass", "minecraft:tall_grass"), true, "");
            case "cherry_grove" -> create(biomeId,
                VegetationProfile.Family.MEADOW,
                0.56, 0.34, 0.68, 0.34, 0.08, 0.08, 0.04,
                0.28, 0.52, 164,
                List.of(VegetationProfile.TreeShape.CHERRY_TERRACE),
                Set.of("minecraft:pink_petals", "minecraft:short_grass"), true, "");
            case "mushroom_fields" -> create(biomeId,
                VegetationProfile.Family.MYCELIAL,
                0.48, 0.42, 0.58, 0.02, 0.10, 0.12, 0.07,
                0.22, 0.60, 160,
                List.of(VegetationProfile.TreeShape.GIANT_MUSHROOM),
                Set.of("minecraft:red_mushroom", "minecraft:brown_mushroom"), true, "");
            case "beach", "snowy_beach", "stony_shore" -> create(biomeId,
                VegetationProfile.Family.COASTAL,
                0.0, 0.06, 0.22, 0.01, 0.15, 0.24, 0,
                0.70, 0.30, 90, List.of(),
                Set.of("minecraft:short_grass"), true, "");
            case "snowy_plains", "ice_spikes", "frozen_river" -> create(biomeId,
                VegetationProfile.Family.POLAR,
                0.015, 0.05, 0.10, 0, 0.02, 0.18, 0,
                0.72, 0.32, 116,
                List.of(VegetationProfile.TreeShape.SPRUCE_CONICAL),
                Set.of("minecraft:short_grass"), true, "");
            case "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks",
                 "windswept_hills", "windswept_gravelly_hills",
                 "windswept_forest" -> create(biomeId,
                VegetationProfile.Family.ALPINE_MARGIN,
                path.equals("windswept_forest") ? 0.28 : 0.0,
                0.10, 0.20, 0.02, 0.03, 0.38, 0.004,
                0.55, 0.38, 146,
                path.equals("windswept_forest")
                    ? List.of(VegetationProfile.TreeShape.SPRUCE_CONICAL)
                    : List.of(),
                Set.of("minecraft:short_grass"), true, "");
            default -> create(biomeId,
                VegetationProfile.Family.TEMPERATE_FOREST,
                0.30, 0.30, 0.52, 0.10, 0.08, 0.10, 0.015,
                0.30, 0.55, 156,
                List.of(VegetationProfile.TreeShape.OAK_ROUNDED),
                Set.of("minecraft:short_grass"), true, "");
        };
    }

    private static VegetationProfile create(
        String biomeId,
        VegetationProfile.Family family,
        double tree,
        double shrub,
        double ground,
        double flower,
        double deadwood,
        double rock,
        double oldGrowth,
        double clearing,
        double maxSlope,
        int treeLine,
        List<VegetationProfile.TreeShape> shapes,
        Set<String> groundPalette,
        boolean terrestrial,
        String absence
    ) {
        return new VegetationProfile(
            biomeId,
            "nexus_landscape:vegetation/"
                + biomeId.substring("minecraft:".length()),
            family,
            tree,
            shrub,
            ground,
            flower,
            deadwood,
            rock,
            oldGrowth,
            clearing,
            maxSlope,
            treeLine,
            shapes,
            groundPalette,
            terrestrial,
            absence
        );
    }
}
