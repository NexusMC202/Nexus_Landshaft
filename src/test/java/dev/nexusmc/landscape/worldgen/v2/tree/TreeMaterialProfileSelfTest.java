package dev.nexusmc.landscape.worldgen.v2.tree;

import net.minecraft.world.level.block.Blocks;

/** Verifies species material ids and strict Minecraft registry resolution. */
public final class TreeMaterialProfileSelfTest {
    private TreeMaterialProfileSelfTest() {
    }

    public static void main(String[] args) {
        verifyPureProfiles();
        verifyRegistryResolution();
        verifyInvalidProfilesFail();
        System.out.println("TreeMaterialProfileSelfTest: PASS");
    }

    private static void verifyPureProfiles() {
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            TreeMaterialProfile materials = species.materialProfile();
            require(materials != null, species + " material profile is missing");
            require(materials.logBlockId().startsWith("minecraft:"),
                species + " log material is not namespaced");
            require(materials.leavesBlockId().startsWith("minecraft:"),
                species + " leaves material is not namespaced");
            require(!materials.logBlockId().equals(materials.leavesBlockId()),
                species + " log and leaves ids must differ");
        }

        require(ConiferSpeciesProfile.SPRUCE.materialProfile()
                == TreeMaterialProfile.SPRUCE,
            "spruce material binding changed unexpectedly");
        require(ConiferSpeciesProfile.PINE.materialProfile()
                == TreeMaterialProfile.SPRUCE,
            "vanilla pine must currently use spruce materials");
    }

    private static void verifyRegistryResolution() {
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            TreeMaterialResolver.ResolvedMaterials resolved =
                TreeMaterialResolver.resolve(species.materialProfile());
            require(resolved.logBlock() == Blocks.SPRUCE_LOG,
                species + " log registry mapping mismatch");
            require(resolved.leavesBlock() == Blocks.SPRUCE_LEAVES,
                species + " leaves registry mapping mismatch");
            require(resolved.logBlock() != resolved.leavesBlock(),
                species + " resolved materials unexpectedly alias");
        }

        expectFailure(() -> TreeMaterialResolver.resolve(new TreeMaterialProfile(
            "minecraft:not_a_real_tree_log",
            "minecraft:spruce_leaves"
        )));
    }

    private static void verifyInvalidProfilesFail() {
        expectFailure(() -> new TreeMaterialProfile(
            "spruce_log",
            "minecraft:spruce_leaves"
        ));
        expectFailure(() -> new TreeMaterialProfile(
            "minecraft:spruce_log",
            ""
        ));
        expectFailure(() -> new TreeMaterialProfile(
            "minecraft:spruce_log",
            "minecraft:spruce_log"
        ));
        expectFailure(() -> TreeMaterialResolver.resolve(null));
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected validation failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
