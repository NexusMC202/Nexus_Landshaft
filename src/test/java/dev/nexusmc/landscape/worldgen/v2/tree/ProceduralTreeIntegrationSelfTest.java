package dev.nexusmc.landscape.worldgen.v2.tree;

/** Pure policy checks for the runtime integration boundary. */
public final class ProceduralTreeIntegrationSelfTest {
    private ProceduralTreeIntegrationSelfTest() {
    }

    public static void main(String[] args) {
        require(
            !ProceduralTreeIntegration.Result.PLACED.shouldFallback(),
            "placed tree must not fall back"
        );
        require(
            ProceduralTreeIntegration.Result.COLLISION.shouldFallback(),
            "collision must fall back"
        );
        require(
            ProceduralTreeIntegration.Result.DISABLED.shouldFallback(),
            "disabled v2 must fall back"
        );
        require(
            ProceduralTreeIntegration.Result.UNSUPPORTED.shouldFallback(),
            "unsupported species must fall back"
        );
        require(
            ProceduralTreeIntegration.Result.values().length == 4,
            "integration result contract changed unexpectedly"
        );
        System.out.println("ProceduralTreeIntegrationSelfTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
