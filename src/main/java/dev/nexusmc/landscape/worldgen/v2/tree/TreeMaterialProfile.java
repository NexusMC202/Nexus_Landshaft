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
