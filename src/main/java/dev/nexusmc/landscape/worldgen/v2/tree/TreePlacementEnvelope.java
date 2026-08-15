package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Cheap conservative bounds resolved before branch generation and voxelization.
 * Worldgen can reject obviously blocked candidates using a handful of probes
 * instead of allocating a complete graph and block model.
 */
public record TreePlacementEnvelope(
    int horizontalRadius,
    int height,
    int undergroundDepth,
    int probeCount
) {
    public TreePlacementEnvelope {
        if (horizontalRadius < 1 || height < 4 || undergroundDepth < 0) {
            throw new IllegalArgumentException("invalid tree placement envelope");
        }
        if (probeCount < 5 || probeCount > 64) {
            throw new IllegalArgumentException("invalid envelope probe count");
        }
    }

    public static TreePlacementEnvelope estimate(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment,
        boolean oldGrowth
    ) {
        if (species == null || quality == null || environment == null) {
            throw new IllegalArgumentException("envelope inputs are required");
        }

        double baseHeight = switch (quality) {
            case BASIC -> 9.0;
            case MID -> 16.0;
            case HERO -> 27.0;
        };
        double heightScale = species.heightMultiplier()
            * (1.0 + environment.forestCompetition() * 0.18)
            * (1.0 - environment.slope() * 0.08)
            * (oldGrowth ? 1.08 : 1.0);
        int height = (int)Math.ceil(baseHeight * heightScale);
        if (species != ConiferSpeciesProfile.SPRUCE
            && species != ConiferSpeciesProfile.PINE) {
            height++;
        }
        height = Math.min(height, quality.budget().maxHeight());

        double baseRadius = switch (quality) {
            case BASIC -> 3.0;
            case MID -> 5.0;
            case HERO -> 9.0;
        };
        double radiusScale = species.branchLengthMultiplier()
            * (0.88 + environment.openSpaceStrength() * 0.24)
            * (1.0 - environment.forestCompetition() * 0.12);
        int radius = (int)Math.ceil(baseRadius * radiusScale);
        radius += WindDeformationPlan.conservativeExtraRadius(quality, environment);

        // The analytical radius follows branch reach, while rasterization and canopy
        // clusters occupy additional voxels around that skeleton. HERO crowns use the
        // widest clusters, so reserve one more voxel there instead of relying on a
        // seed-specific constant discovered by the final placement pass.
        int canopyMargin = switch (quality) {
            case BASIC, MID -> 2;
            case HERO -> 3;
        };
        if (species != ConiferSpeciesProfile.SPRUCE
            && species != ConiferSpeciesProfile.PINE) {
            canopyMargin += 3;
        }
        if (species.branchLengthMultiplier() >= 1.25) {
            canopyMargin++;
        }
        radius += canopyMargin;
        radius = Math.max(2, Math.min(radius, quality.budget().maxHorizontalRadius()));

        int underground = quality == TreeQualityTier.BASIC ? 1 : 2;
        int probes = switch (quality) {
            case BASIC -> 9;
            case MID -> 17;
            case HERO -> 29;
        };
        return new TreePlacementEnvelope(radius, height, underground, probes);
    }

    public int estimatedVolume() {
        int width = horizontalRadius * 2 + 1;
        return Math.multiplyExact(
            Math.multiplyExact(width, width),
            height + undergroundDepth
        );
    }
}
