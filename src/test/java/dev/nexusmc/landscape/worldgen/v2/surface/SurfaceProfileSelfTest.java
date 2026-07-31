package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Framework-free Stage 6 profile and pure-selection checks.
 */
public final class SurfaceProfileSelfTest {
    private static final Set<String> EXPECTED = Set.of(
        "plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert",
        "swamp", "mangrove_swamp", "forest", "flower_forest", "birch_forest",
        "dark_forest", "old_growth_birch_forest", "old_growth_pine_taiga",
        "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna",
        "savanna_plateau", "windswept_hills", "windswept_gravelly_hills",
        "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle",
        "bamboo_jungle", "badlands", "eroded_badlands", "wooded_badlands",
        "meadow", "cherry_grove", "grove", "snowy_slopes", "frozen_peaks",
        "jagged_peaks", "stony_peaks", "river", "frozen_river", "beach",
        "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean",
        "deep_lukewarm_ocean", "ocean", "deep_ocean", "cold_ocean",
        "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean",
        "mushroom_fields", "dripstone_caves", "lush_caves", "deep_dark"
    );

    private SurfaceProfileSelfTest() {
    }

    public static void main(String[] arguments) {
        verifyCompleteCatalog();
        verifyEveryProfileIsStructured();
        verifyZonePrecedence();
        verifyFallbackWithoutOptionalMods();
        verifyTransitionNoise();
        verifyOrderIndependence();
        verifyThreadSafety();
        System.out.println("SurfaceProfileSelfTest: PASS profiles=53");
    }

    private static void verifyFallbackWithoutOptionalMods() {
        require(
            SurfaceProfileCatalog.find("natures_spirit:fir_forest").isEmpty(),
            "optional biome unexpectedly became a hard catalog dependency"
        );
        require(
            SurfaceProfileCatalog.fallback(-0.8, 0.1, false)
                .biomeId().equals("minecraft:snowy_plains"),
            "cold fallback is not climate-aware"
        );
        require(
            SurfaceProfileCatalog.fallback(0.8, -0.7, false)
                .biomeId().equals("minecraft:desert"),
            "hot dry fallback is not climate-aware"
        );
        require(
            SurfaceProfileCatalog.fallback(0.1, 0.8, false)
                .biomeId().equals("minecraft:swamp"),
            "wet fallback is not climate-aware"
        );
        require(
            SurfaceProfileCatalog.fallback(0.1, 0.8, true)
                .biomeId().equals("minecraft:lush_caves"),
            "underground fallback applied a surface profile"
        );
    }

    private static void verifyTransitionNoise() {
        long seed = -918_273_645L;
        double seamLeft = SurfaceNoise.value(seed, 15, -17, 48, 91L);
        double seamRight = SurfaceNoise.value(seed, 16, -17, 48, 91L);
        double seamTop = SurfaceNoise.value(seed, -17, 15, 48, 91L);
        double seamBottom = SurfaceNoise.value(seed, -17, 16, 48, 91L);
        require(
            Math.abs(seamLeft - seamRight) < 0.08,
            "artificial x chunk seam in transition noise"
        );
        require(
            Math.abs(seamTop - seamBottom) < 0.08,
            "artificial z chunk seam in transition noise"
        );
        require(
            SurfaceNoise.value(seed, 8192, -4096, 48, 91L)
                != SurfaceNoise.value(seed + 1, 8192, -4096, 48, 91L),
            "different seeds produced identical transition noise"
        );
        Set<Long> paletteHashes = new HashSet<>();
        for (int z = 0; z < 64; z += 4) {
            for (int x = 0; x < 64; x += 4) {
                paletteHashes.add(
                    Math.floorMod(
                        SurfaceNoise.hash(seed, x, z, 0x70A11L),
                        7L
                    )
                );
            }
        }
        require(
            paletteHashes.size() >= 5,
            "material distribution collapsed to one/few variants: "
                + paletteHashes
        );
    }

    private static void verifyCompleteCatalog() {
        Set<String> expectedIds = new HashSet<>();
        for (String path : EXPECTED) {
            expectedIds.add("minecraft:" + path);
        }
        require(
            SurfaceProfileCatalog.profiles().keySet().equals(expectedIds),
            "catalog mismatch missing="
                + difference(expectedIds, SurfaceProfileCatalog.profiles().keySet())
                + " extra="
                + difference(SurfaceProfileCatalog.profiles().keySet(), expectedIds)
        );
    }

    private static void verifyEveryProfileIsStructured() {
        Set<String> profileIds = new HashSet<>();
        for (SurfaceProfile profile : SurfaceProfileCatalog.profiles().values()) {
            require(
                profileIds.add(profile.profileId()),
                "duplicate profile id " + profile.profileId()
            );
            require(
                profile.visualTraits().size() >= 3,
                "fewer than three traits for " + profile.biomeId()
            );
            require(
                profile.layers().top().stream().noneMatch(id -> id.contains("vanilla")),
                "vanilla sentinel in " + profile.biomeId()
            );
            SurfaceSelection selection = SurfaceProfileResolver.resolve(
                profile,
                context(0.0, 0.0, 0.0, 0.0, 72.0, 0.08)
            );
            require(
                selection.zone() == SurfaceSelection.Zone.BASE,
                "neutral context did not select BASE for " + profile.biomeId()
            );
        }
    }

    private static void verifyZonePrecedence() {
        SurfaceProfile profile = SurfaceProfileCatalog.require("minecraft:plains");
        requireZone(profile, context(0.9, 0.8, 0.9, 0.9, 220.0, 0.8),
            SurfaceSelection.Zone.CHANNEL);
        requireZone(profile, context(0.0, 0.8, 0.9, 0.9, 220.0, 0.8),
            SurfaceSelection.Zone.LAKE_SHORE);
        requireZone(profile, context(0.0, 0.0, 0.0, 0.8, 72.0, 0.8),
            SurfaceSelection.Zone.COAST);
        require(
            SurfaceProfileResolver.resolve(
                profile,
                context(0.0, 0.0, 0.0, 0.9, 180.0, 0.0)
            ).zone() != SurfaceSelection.Zone.COAST,
            "broad coast field painted a highland"
        );
        requireZone(
            profile,
            context(0.0, 0.0, 0.0, 0.0, 82.0, 0.20),
            SurfaceSelection.Zone.EXPOSED_SLOPE
        );
        requireZone(profile, context(0.0, 0.0, 0.0, 0.0, 220.0, 0.8),
            SurfaceSelection.Zone.ALPINE);
        requireZone(profile, context(0.0, 0.0, 0.0, 0.0, 80.0, 0.8),
            SurfaceSelection.Zone.EXPOSED_SLOPE);
    }

    private static void verifyOrderIndependence() {
        List<String> ids = new ArrayList<>(
            SurfaceProfileCatalog.profiles().keySet()
        );
        List<SurfaceSelection> baseline = resolve(ids);
        List<String> reversed = new ArrayList<>(ids);
        Collections.reverse(reversed);
        List<SurfaceSelection> reverseResults = resolve(reversed);
        Collections.reverse(reverseResults);
        require(
            baseline.equals(reverseResults),
            "surface selection changed with reverse query order"
        );
    }

    private static void verifyThreadSafety() {
        List<String> ids = new ArrayList<>(
            SurfaceProfileCatalog.profiles().keySet()
        );
        List<SurfaceSelection> expected = resolve(ids);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<SurfaceSelection>> futures = new ArrayList<>();
            for (String id : ids) {
                futures.add(executor.submit(() -> resolve(id)));
            }
            for (int index = 0; index < futures.size(); index++) {
                try {
                    require(
                        expected.get(index).equals(futures.get(index).get()),
                        "parallel selection mismatch for " + ids.get(index)
                    );
                } catch (Exception exception) {
                    throw new AssertionError(
                        "parallel surface selection failed",
                        exception
                    );
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<SurfaceSelection> resolve(List<String> ids) {
        List<SurfaceSelection> result = new ArrayList<>();
        for (String id : ids) {
            result.add(resolve(id));
        }
        return result;
    }

    private static SurfaceSelection resolve(String biomeId) {
        int index = Math.floorMod(biomeId.hashCode(), 997);
        double riverMask = index % 11 == 0 ? 0.55 : 0.0;
        double lakeMask = index % 13 == 0 ? 0.35 : 0.0;
        double coast = index % 7 == 0 ? 0.70 : 0.0;
        double volcanic = index % 17 == 0 ? 0.68 : 0.0;
        double elevation = 62.0 + index * 2.0;
        double slope = (index % 9) / 10.0;
        return SurfaceProfileResolver.resolve(
            SurfaceProfileCatalog.require(biomeId),
            context(
                riverMask,
                lakeMask,
                volcanic,
                coast,
                elevation,
                slope
            )
        );
    }

    private static SurfaceContext context(
        double riverMask,
        double lakeMask,
        double volcanic,
        double coast,
        double elevation,
        double slope
    ) {
        return new SurfaceContext(
            24_0802L,
            128,
            -256,
            (int)Math.round(elevation),
            "minecraft:plains",
            "sedimentary_lowland",
            0.0,
            0.0,
            0.1,
            0.0,
            0.0,
            elevation,
            Math.max(0.0, Math.min(1.0, (elevation + 64.0) / 384.0)),
            slope,
            riverMask > 0.0 ? 0.0 : 128.0,
            riverMask,
            riverMask,
            lakeMask,
            coast,
            128.0 * (1.0 - coast),
            0.2,
            volcanic,
            elevation > 170.0 ? 0.6 : 0.0,
            elevation > 150.0 ? 0.7 : 0.0,
            0.1,
            0.1,
            0.0,
            0.0,
            0.37,
            0.63
        );
    }

    private static void requireZone(
        SurfaceProfile profile,
        SurfaceContext context,
        SurfaceSelection.Zone expected
    ) {
        SurfaceSelection.Zone actual =
            SurfaceProfileResolver.resolve(profile, context).zone();
        require(
            actual == expected,
            "zone precedence expected=" + expected + " actual=" + actual
        );
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
