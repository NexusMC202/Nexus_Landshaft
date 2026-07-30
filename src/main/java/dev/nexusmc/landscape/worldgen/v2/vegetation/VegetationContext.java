package dev.nexusmc.landscape.worldgen.v2.vegetation;

import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContext;

public record VegetationContext(
    SurfaceContext surface,
    double forestCore,
    double clearingNoise,
    boolean underground,
    boolean waterAtSurface
) {
    public VegetationContext {
        if (surface == null
            || !Double.isFinite(forestCore)
            || !Double.isFinite(clearingNoise)
            || forestCore < 0.0
            || forestCore > 1.0
            || clearingNoise < 0.0
            || clearingNoise > 1.0) {
            throw new IllegalArgumentException("invalid vegetation context");
        }
    }
}
