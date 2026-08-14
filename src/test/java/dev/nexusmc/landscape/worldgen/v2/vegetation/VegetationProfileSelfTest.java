package dev.nexusmc.landscape.worldgen.v2.vegetation;

import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContext;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfileCatalog;
import dev.nexusmc.landscape.worldgen.v2.tree.ProceduralTreePolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class VegetationProfileSelfTest {
    private VegetationProfileSelfTest() {
    }

    public static void main(String[] arguments) {
        verifyCoverage();
        verifyDensityContracts();
        verifyExclusions();
        verifyProceduralConiferSiteContract();
        verifyOrderAndThreads();
        System.out.println("VegetationProfileSelfTest: PASS profiles=53");
    }

    private static void verifyCoverage() {
        require(
            VegetationProfileCatalog.profiles().keySet()
                .equals(SurfaceProfileCatalog.profiles().keySet()),
            "surface/vegetation profile key mismatch"
        );
        require(
            VegetationProfileCatalog.find("natures_spirit:fir_forest").isEmpty(),
            "Nature's Spirit became a hard dependency"
        );
        require(
            VegetationProfileCatalog.fallback(0.8, -0.8, false)
                .biomeId().equals("minecraft:desert"),
            "modded hot/dry fallback mismatch"
        );
    }

    private static void verifyDensityContracts() {
        for (VegetationProfile profile
            : VegetationProfileCatalog.profiles().values()) {
            if (!profile.terrestrial()) {
                require(
                    !profile.intentionalAbsence().isBlank(),
                    "missing intentional absence: " + profile.biomeId()
                );
                continue;
            }
            require(
                profile.groundDensity() > 0.0
                    && profile.groundDensity() < 1.0,
                "terrestrial ground density collapsed to 0/1: "
                    + profile.biomeId()
            );
        }
    }

    private static void verifyExclusions() {
        VegetationProfile forest =
            VegetationProfileCatalog.require("minecraft:forest");
        VegetationSelection normal = resolve(forest, 0.08, 0.0, 80, false, false);
        VegetationSelection steep = resolve(forest, 0.90, 0.0, 80, false, false);
        VegetationSelection river = resolve(forest, 0.08, 0.90, 80, false, false);
        VegetationSelection alpine = resolve(forest, 0.08, 0.0, 220, false, false);
        VegetationSelection underground = resolve(forest, 0.08, 0.0, 20, true, false);
        VegetationSelection water = resolve(forest, 0.08, 0.0, 80, false, true);
        require(
            steep.treeDensity() < normal.treeDensity() * 0.15,
            "trees not reduced on steep slopes"
        );
        require(
            river.treeDensity() < normal.treeDensity() * 0.15,
            "trees not reduced in active river center"
        );
        require(
            alpine.treeDensity() < normal.treeDensity() * 0.15,
            "treeline did not reduce high trees"
        );
        require(
            !underground.terrestrialAllowed()
                && underground.treeDensity() == 0.0,
            "terrestrial vegetation applied underground"
        );
        require(
            !water.terrestrialAllowed()
                && !water.treesAllowed()
                && water.treeDensity() == 0.0,
            "terrestrial trees applied on water surface"
        );
    }

    private static void verifyProceduralConiferSiteContract() {
        VegetationProfile taiga =
            VegetationProfileCatalog.require("minecraft:taiga");
        VegetationSelection normal = resolve(taiga, 0.08, 0.0, 86, false, false);
        VegetationSelection channel = resolve(taiga, 0.08, 0.90, 86, false, false);
        VegetationSelection water = resolve(taiga, 0.08, 0.0, 86, false, true);

        require(normal.treesAllowed() && normal.treeDensity() > 0.002,
            "taiga procedural conifers rejected on normal terrain");
        require(!channel.treesAllowed(),
            "taiga procedural conifers allowed in active river channel");
        require(!water.treesAllowed(),
            "taiga procedural conifers allowed on water surface");
        for (VegetationProfile.TreeShape shape : taiga.treeShapes()) {
            require(ProceduralTreePolicy.supports(shape),
                "taiga tree shape bypasses Tree System v2: " + shape);
        }
    }

    private static void verifyOrderAndThreads() {
        List<String> ids = new ArrayList<>(
            VegetationProfileCatalog.profiles().keySet()
        );
        List<VegetationSelection> expected = resolve(ids);
        List<String> reverse = new ArrayList<>(ids);
        Collections.reverse(reverse);
        List<VegetationSelection> reverseResult = resolve(reverse);
        Collections.reverse(reverseResult);
        require(
            expected.equals(reverseResult),
            "vegetation changed with reverse query order"
        );
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<VegetationSelection>> futures = new ArrayList<>();
            for (String id : ids) {
                futures.add(executor.submit(() -> resolve(
                    VegetationProfileCatalog.require(id),
                    0.16,
                    0.0,
                    92,
                    false,
                    false
                )));
            }
            for (int index = 0; index < futures.size(); index++) {
                try {
                    require(
                        expected.get(index).equals(futures.get(index).get()),
                        "parallel vegetation mismatch: " + ids.get(index)
                    );
                } catch (Exception exception) {
                    throw new AssertionError("parallel vegetation failed", exception);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<VegetationSelection> resolve(List<String> ids) {
        List<VegetationSelection> result = new ArrayList<>();
        for (String id : ids) {
            result.add(resolve(
                VegetationProfileCatalog.require(id),
                0.16,
                0.0,
                92,
                false,
                false
            ));
        }
        return result;
    }

    private static VegetationSelection resolve(
        VegetationProfile profile,
        double slope,
        double river,
        int surfaceY,
        boolean underground,
        boolean waterAtSurface
    ) {
        SurfaceContext surface = new SurfaceContext(
            240802L, 128, -256, surfaceY, profile.biomeId(),
            "sedimentary_lowland", 0.1, 0.3, 0.4, 0.0, 0.0,
            surfaceY, Math.max(0.0, Math.min(1.0, (surfaceY + 64) / 384.0)),
            slope, river > 0 ? 0.0 : 128.0, river, river,
            0.0, 0.0, 192.0, 0.4, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.4, 0.6
        );
        VegetationContext context = new VegetationContext(
            surface,
            0.8,
            0.8,
            underground,
            waterAtSurface
        );
        return VegetationResolver.resolve(profile, context);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
