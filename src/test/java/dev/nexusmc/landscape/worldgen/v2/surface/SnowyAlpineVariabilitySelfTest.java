package dev.nexusmc.landscape.worldgen.v2.surface;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Verifies measurable deterministic variation across snowy/alpine surfaces. */
public final class SnowyAlpineVariabilitySelfTest {
    private static final List<String> BIOMES = List.of(
        "minecraft:snowy_plains",
        "minecraft:snowy_taiga",
        "minecraft:grove",
        "minecraft:snowy_slopes",
        "minecraft:frozen_peaks",
        "minecraft:jagged_peaks",
        "minecraft:stony_peaks"
    );

    private SnowyAlpineVariabilitySelfTest() {
    }

    public static void main(String[] args) {
        Set<Signature> regionalSignatures = new HashSet<>();
        for (String biome : BIOMES) {
            SurfaceProfile profile = SurfaceProfileCatalog.require(biome);
            Set<Signature> signatures = new HashSet<>();
            Set<String> materials = new HashSet<>();
            for (ContextCase contextCase : contexts()) {
                SurfaceSelection first = SurfaceProfileResolver.resolve(
                    profile,
                    context(biome, contextCase)
                );
                SurfaceSelection repeat = SurfaceProfileResolver.resolve(
                    profile,
                    context(biome, contextCase)
                );
                require(first.equals(repeat),
                    biome + " surface selection is not deterministic");
                Signature signature = new Signature(
                    first.zone(),
                    first.topPalette(),
                    first.substratePalette(),
                    first.depth()
                );
                signatures.add(signature);
                regionalSignatures.add(signature);
                materials.addAll(first.topPalette());
                materials.addAll(first.substratePalette());
            }
            require(signatures.size() >= 3,
                biome + " collapsed surface signatures: " + signatures.size());
            require(materials.size() >= 3,
                biome + " collapsed material variation: " + materials);
        }
        require(regionalSignatures.size() >= 12,
            "snowy/alpine regions collapsed to shared signatures: "
                + regionalSignatures.size());
        System.out.println("SnowyAlpineVariabilitySelfTest: PASS biomes="
            + BIOMES.size() + " signatures=" + regionalSignatures.size());
    }

    private static List<ContextCase> contexts() {
        return List.of(
            new ContextCase(88.0, 0.03, 0.0, 0.0, 0.12),
            new ContextCase(112.0, 0.34, 0.0, 0.0, 0.08),
            new ContextCase(168.0, 0.10, 0.72, 0.10, 0.22),
            new ContextCase(196.0, 0.24, 0.86, 0.68, 0.36),
            new ContextCase(224.0, 0.46, 0.94, 0.84, 0.18)
        );
    }

    private static SurfaceContext context(
        String biome,
        ContextCase value
    ) {
        return new SurfaceContext(
            0x534E4F57414C504CL,
            (int)value.elevation() * 7,
            (int)value.elevation() * -11,
            (int)value.elevation(),
            biome,
            "glacial_mountain",
            -0.65,
            0.18,
            0.45,
            -0.20,
            0.35,
            value.elevation(),
            Math.min(1.0, (value.elevation() + 64.0) / 384.0),
            value.slope(),
            128.0,
            0.0,
            0.0,
            0.0,
            0.0,
            220.0,
            0.20,
            0.0,
            value.glacier(),
            value.alpine(),
            0.0,
            0.0,
            0.0,
            0.0,
            value.localNoise(),
            0.47
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record ContextCase(
        double elevation,
        double slope,
        double alpine,
        double glacier,
        double localNoise
    ) {
    }

    private record Signature(
        SurfaceSelection.Zone zone,
        List<String> top,
        List<String> substrate,
        int depth
    ) {
    }
}
