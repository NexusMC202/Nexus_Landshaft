package dev.nexusmc.landscape.command;

import java.util.Set;

/** Pure command contract shared by runtime registration and standalone tests. */
public final class Stage6CommandPolicy {
    public static final int REQUIRED_PERMISSION_LEVEL = 2;
    public static final Set<String> DEBUG_SUBCOMMANDS = Set.of(
        "position", "chunk", "counters", "reset", "export"
    );

    private Stage6CommandPolicy() {
    }

    public static boolean canUse(int permissionLevel) {
        return permissionLevel >= REQUIRED_PERMISSION_LEVEL;
    }
}
