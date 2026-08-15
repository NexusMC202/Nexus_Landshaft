package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Pure block-id material description kept separate from Minecraft runtime
 * classes so species data can be tested and exported off-thread.
 */
public record TreeMaterialProfile(
    String logBlockId,
    String leavesBlockId
) {
    public static final TreeMaterialProfile SPRUCE = new TreeMaterialProfile(
        "minecraft:spruce_log",
        "minecraft:spruce_leaves"
    );
    public static final TreeMaterialProfile OAK = new TreeMaterialProfile(
        "minecraft:oak_log", "minecraft:oak_leaves"
    );
    public static final TreeMaterialProfile BIRCH = new TreeMaterialProfile(
        "minecraft:birch_log", "minecraft:birch_leaves"
    );
    public static final TreeMaterialProfile DARK_OAK = new TreeMaterialProfile(
        "minecraft:dark_oak_log", "minecraft:dark_oak_leaves"
    );
    public static final TreeMaterialProfile JUNGLE = new TreeMaterialProfile(
        "minecraft:jungle_log", "minecraft:jungle_leaves"
    );
    public static final TreeMaterialProfile ACACIA = new TreeMaterialProfile(
        "minecraft:acacia_log", "minecraft:acacia_leaves"
    );
    public static final TreeMaterialProfile CHERRY = new TreeMaterialProfile(
        "minecraft:cherry_log", "minecraft:cherry_leaves"
    );
    public static final TreeMaterialProfile MANGROVE = new TreeMaterialProfile(
        "minecraft:mangrove_log", "minecraft:mangrove_leaves"
    );

    public TreeMaterialProfile {
        logBlockId = requireBlockId(logBlockId, "logBlockId");
        leavesBlockId = requireBlockId(leavesBlockId, "leavesBlockId");
        if (logBlockId.equals(leavesBlockId)) {
            throw new IllegalArgumentException("log and leaves materials must differ");
        }
    }

    private static String requireBlockId(String value, String name) {
        if (value == null || value.isBlank() || !value.contains(":")) {
            throw new IllegalArgumentException(name + " must be a namespaced block id");
        }
        return value;
    }
}
