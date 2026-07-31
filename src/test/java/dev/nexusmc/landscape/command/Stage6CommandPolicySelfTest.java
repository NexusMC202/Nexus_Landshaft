package dev.nexusmc.landscape.command;

public final class Stage6CommandPolicySelfTest {
    private Stage6CommandPolicySelfTest() {
    }

    public static void main(String[] args) {
        require(!Stage6CommandPolicy.canUse(0), "level 0 gained access");
        require(!Stage6CommandPolicy.canUse(1), "level 1 gained access");
        require(Stage6CommandPolicy.canUse(2), "level 2 denied access");
        require(Stage6CommandPolicy.canUse(4), "level 4 denied access");
        require(Stage6CommandPolicy.DEBUG_SUBCOMMANDS.equals(java.util.Set.of(
            "position", "chunk", "counters", "reset", "export"
        )), "debug command inventory differs");
        System.out.println("Stage6CommandPolicySelfTest: PASS commands=5 permission=2");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
