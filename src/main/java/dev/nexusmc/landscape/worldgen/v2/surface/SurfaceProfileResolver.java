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
        double river = smoothstep(
            0.16,
            0.62,
            Math.max(context.riverMask(), context.riverInfluence())
        );
        if (accept(river, context.localNoise())) {
            return selection(
                profile,
                SurfaceSelection.Zone.CHANNEL,
                river,
                layers.sediment(),
                layers.transition(),
                Math.min(5, profile.soilDepth() + 1)
            );
        }
        double lake = smoothstep(0.08, 0.52, context.lakeBasinMask());
        if (accept(lake, context.localNoise())) {
            return selection(
                profile,
                SurfaceSelection.Zone.LAKE_SHORE,
                lake,
                layers.wet(),
                layers.sediment(),
                Math.min(5, profile.soilDepth() + 1)
            );
        }
        double bank = Math.max(
            smoothstep(26.0, 4.0, context.riverDistance())
                * (1.0 - river),
            smoothstep(0.58, 0.88, context.groundwater())
        );
        if (accept(bank, context.localNoise())) {
            return selection(
                profile,
                SurfaceSelection.Zone.WET_BANK,
                bank,
                layers.wet(),
                layers.soil(),
                profile.soilDepth()
            );
        }
        double coast = smoothstep(0.38, 0.78, context.coastWeight());
        if (accept(coast, context.localNoise())) {
            return selection(
                profile,
                SurfaceSelection.Zone.COAST,
                coast,
                layers.coast(),
                layers.sediment(),
                Math.min(5, profile.soilDepth() + 1)
            );
        }
        double volcanic = smoothstep(0.44, 0.78, context.volcanicWeight());
        if (accept(volcanic, context.localNoise())) {
            return selection(
                profile,
                SurfaceSelection.Zone.VOLCANIC,
                volcanic,
                layers.exposedRock(),
                layers.transition(),
                Math.max(2, profile.soilDepth() - 1)
            );
        }
        double alpine = Math.max(
            context.alpineInfluence(),
            Math.max(
                smoothstep(132.0, 196.0, context.elevation()),
                context.glacierWeight()
            )
        );
        if (accept(alpine, context.localNoise())) {
            return selection(
                profile,
                SurfaceSelection.Zone.ALPINE,
                alpine,
                layers.alpine(),
                layers.exposedRock(),
                Math.max(1, profile.soilDepth() - 1)
            );
        }
        double exposed = smoothstep(0.28, 0.72, context.slope());
        if (accept(exposed, context.localNoise())) {
            return selection(
                profile,
                SurfaceSelection.Zone.EXPOSED_SLOPE,
                exposed,
                layers.exposedRock(),
                layers.transition(),
                Math.max(1, profile.soilDepth() - 1)
            );
        }
        return selection(
            profile,
            SurfaceSelection.Zone.BASE,
            1.0,
            layers.top(),
            layers.soil(),
            profile.soilDepth()
        );
    }

    private static SurfaceSelection selection(
        SurfaceProfile profile,
        SurfaceSelection.Zone zone,
        double weight,
        java.util.List<String> top,
        java.util.List<String> substrate,
        int depth
    ) {
        return new SurfaceSelection(
            profile.profileId(),
            zone,
            clamp01(weight),
            top,
            substrate,
            depth
        );
    }

    private static boolean accept(double weight, double localNoise) {
        return weight >= 0.12
            && weight >= 0.22 + localNoise * 0.58;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 < edge0) {
            return 1.0 - smoothstep(edge1, edge0, value);
        }
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
