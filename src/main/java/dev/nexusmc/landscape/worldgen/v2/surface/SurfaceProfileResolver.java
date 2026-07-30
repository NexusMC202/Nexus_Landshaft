package dev.nexusmc.landscape.worldgen.v2.surface;

/**
 * Central precedence rules shared by future block application, diagnostics and
 * vegetation exclusion. No neighbour/chunk reads are performed here.
 */
public final class SurfaceProfileResolver {
    private SurfaceProfileResolver() {
    }

    public static SurfaceSelection resolve(
        SurfaceProfile profile,
        SurfaceContext context
    ) {
        SurfaceProfile.Layers layers = profile.layers();
        if (context.activeChannel()) {
            return selection(
                profile,
                SurfaceSelection.Zone.CHANNEL,
                layers.sediment(),
                layers.transition(),
                Math.min(5, profile.soilDepth() + 1)
            );
        }
        if (context.lakeBasinMask() >= 0.18) {
            return selection(
                profile,
                SurfaceSelection.Zone.LAKE_SHORE,
                layers.wet(),
                layers.sediment(),
                Math.min(5, profile.soilDepth() + 1)
            );
        }
        if (context.wetBank()) {
            return selection(
                profile,
                SurfaceSelection.Zone.WET_BANK,
                layers.wet(),
                layers.soil(),
                profile.soilDepth()
            );
        }
        if (context.coastWeight() >= 0.58) {
            return selection(
                profile,
                SurfaceSelection.Zone.COAST,
                layers.coast(),
                layers.sediment(),
                Math.min(5, profile.soilDepth() + 1)
            );
        }
        if (context.volcanicWeight() >= 0.62) {
            return selection(
                profile,
                SurfaceSelection.Zone.VOLCANIC,
                layers.exposedRock(),
                layers.transition(),
                Math.max(2, profile.soilDepth() - 1)
            );
        }
        if (context.alpineExposure()) {
            return selection(
                profile,
                SurfaceSelection.Zone.ALPINE,
                layers.alpine(),
                layers.exposedRock(),
                Math.max(1, profile.soilDepth() - 1)
            );
        }
        if (context.exposedSlope()) {
            return selection(
                profile,
                SurfaceSelection.Zone.EXPOSED_SLOPE,
                layers.exposedRock(),
                layers.transition(),
                Math.max(1, profile.soilDepth() - 1)
            );
        }
        return selection(
            profile,
            SurfaceSelection.Zone.BASE,
            layers.top(),
            layers.soil(),
            profile.soilDepth()
        );
    }

    private static SurfaceSelection selection(
        SurfaceProfile profile,
        SurfaceSelection.Zone zone,
        java.util.List<String> top,
        java.util.List<String> substrate,
        int depth
    ) {
        return new SurfaceSelection(
            profile.profileId(),
            zone,
            top,
            substrate,
            depth
        );
    }
}
