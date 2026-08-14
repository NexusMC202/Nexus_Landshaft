package dev.nexusmc.landscape.worldgen.v2.tree;

/** Verifies that spruce and pine preserve distinct calm-crown silhouettes. */
public final class CrownShapeMetricsSelfTest {
    private static final int SAMPLES = 8;
    private static final long ROOT_SEED = 0x534841504543524FL;

    private CrownShapeMetricsSelfTest() {
    }

    public static void main(String[] args) {
        TreeEnvironment calm = new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            0.0, 0.0, 0.68, -0.12, 138
        );
        for (TreeQualityTier quality : TreeQualityTier.values()) {
            Aggregate spruce = measure(ConiferSpeciesProfile.SPRUCE, quality, calm);
            Aggregate pine = measure(ConiferSpeciesProfile.PINE, quality, calm);
            String label = quality.toString();

            require(pine.meanLowerShare + 0.05 < spruce.meanLowerShare,
                label + " pine crown is not sufficiently top-heavy: pine="
                    + pine.meanLowerShare + " spruce=" + spruce.meanLowerShare);
            require(pine.meanY > spruce.meanY + 0.50,
                label + " pine foliage center is not high enough: pine="
                    + pine.meanY + " spruce=" + spruce.meanY);
        }
        System.out.println("CrownShapeMetricsSelfTest: PASS");
    }

    private static Aggregate measure(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        double lowerShare = 0.0;
        double meanY = 0.0;
        for (int index = 0; index < SAMPLES; index++) {
            long seed = TreeLifeHistory.mix(
                ROOT_SEED
                    ^ ((long)species.ordinal() << 56)
                    ^ ((long)quality.ordinal() << 48)
                    ^ index * 0x9E3779B97F4A7C15L
            );
            ProceduralTreePlan plan = new ProceduralTreePlan(
                seed,
                species,
                quality,
                environment,
                quality == TreeQualityTier.HERO
            );
            CrownShapeMetrics metrics = CrownShapeMetrics.measure(
                TreeGenerationPipeline.generate(plan).model()
            );
            lowerShare += metrics.lowerCrownShare();
            meanY += metrics.meanY();
        }
        return new Aggregate(lowerShare / SAMPLES, meanY / SAMPLES);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Aggregate(double meanLowerShare, double meanY) {
    }
}
