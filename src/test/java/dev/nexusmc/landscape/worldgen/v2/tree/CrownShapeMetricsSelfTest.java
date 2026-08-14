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

        Aggregate previousSpruce = null;
        Aggregate previousPine = null;
        for (TreeQualityTier quality : TreeQualityTier.values()) {
            Aggregate spruce = measure(ConiferSpeciesProfile.SPRUCE, quality, calm);
            Aggregate pine = measure(ConiferSpeciesProfile.PINE, quality, calm);
            String label = quality.toString();

            require(spruce.meanLowerShare() >= 0.45 && spruce.meanLowerShare() <= 0.65,
                label + " spruce lower-crown share escaped silhouette range: "
                    + spruce.meanLowerShare());
            require(spruce.meanUpperShare() >= 0.12 && spruce.meanUpperShare() <= 0.28,
                label + " spruce upper-crown share escaped silhouette range: "
                    + spruce.meanUpperShare());
            require(pine.meanLowerShare() >= 0.15 && pine.meanLowerShare() <= 0.42,
                label + " pine lower-crown share escaped silhouette range: "
                    + pine.meanLowerShare());
            require(pine.meanUpperShare() >= 0.28 && pine.meanUpperShare() <= 0.50,
                label + " pine upper-crown share escaped silhouette range: "
                    + pine.meanUpperShare());

            require(pine.meanLowerShare() + 0.08 < spruce.meanLowerShare(),
                label + " pine crown is not sufficiently top-heavy: pine="
                    + pine.meanLowerShare() + " spruce=" + spruce.meanLowerShare());
            require(pine.meanUpperShare() > spruce.meanUpperShare() + 0.10,
                label + " pine upper crown is not distinct enough: pine="
                    + pine.meanUpperShare() + " spruce=" + spruce.meanUpperShare());
            require(pine.meanY() > spruce.meanY() + 0.50,
                label + " pine foliage center is not high enough: pine="
                    + pine.meanY() + " spruce=" + spruce.meanY());

            if (previousSpruce != null) {
                verifyTierGrowth("SPRUCE " + label, previousSpruce, spruce);
                verifyTierGrowth("PINE " + label, previousPine, pine);
            }
            previousSpruce = spruce;
            previousPine = pine;
        }
        System.out.println("CrownShapeMetricsSelfTest: PASS");
    }

    private static void verifyTierGrowth(
        String label,
        Aggregate previous,
        Aggregate current
    ) {
        require(current.meanLeafCount() > previous.meanLeafCount(),
            label + " leaf count did not grow with quality tier");
        require(current.meanHeight() > previous.meanHeight(),
            label + " crown height did not grow with quality tier");
        require(current.meanHorizontalSpan() > previous.meanHorizontalSpan(),
            label + " crown width did not grow with quality tier");
        require(current.meanY() > previous.meanY(),
            label + " foliage center did not grow with quality tier");
    }

    private static Aggregate measure(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        double leaves = 0.0;
        double height = 0.0;
        double span = 0.0;
        double meanY = 0.0;
        double lowerShare = 0.0;
        double upperShare = 0.0;
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
            leaves += metrics.leafCount();
            height += metrics.height();
            span += metrics.horizontalSpan();
            meanY += metrics.meanY();
            lowerShare += metrics.lowerCrownShare();
            upperShare += metrics.upperCrownShare();
        }
        return new Aggregate(
            leaves / SAMPLES,
            height / SAMPLES,
            span / SAMPLES,
            meanY / SAMPLES,
            lowerShare / SAMPLES,
            upperShare / SAMPLES
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Aggregate(
        double meanLeafCount,
        double meanHeight,
        double meanHorizontalSpan,
        double meanY,
        double meanLowerShare,
        double meanUpperShare
    ) {
    }
}
