package dev.nexusmc.landscape.worldgen.v2.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports aggregate calm-crown silhouette metrics for visual diagnostics. */
public final class CrownShapeSummaryExport {
    private static final int FORMAT_VERSION = 1;
    private static final int SAMPLES = 8;
    private static final long ROOT_SEED = 0x534841504543524FL;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CrownShapeSummaryExport() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
            ? Path.of("build", "tree-previews")
            : Path.of(args[0]);
        Files.createDirectories(output);

        TreeEnvironment calm = new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            0.0, 0.0, 0.68, -0.12, 138
        );
        List<Entry> entries = new ArrayList<>();
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                entries.add(new Entry(species, quality, measure(species, quality, calm)));
            }
        }

        Files.writeString(
            output.resolve("crown-shape-summary.json"),
            GSON.toJson(new Export(
                FORMAT_VERSION,
                "nexus_landscape:crown_shape_summary_v1",
                ROOT_SEED,
                SAMPLES,
                calm,
                List.copyOf(entries)
            ))
        );
        System.out.println(
            "CrownShapeSummaryExport: PASS entries=" + entries.size()
                + " output=" + output.toAbsolutePath()
        );
    }

    private static Summary measure(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        double leaves = 0.0;
        double height = 0.0;
        double horizontalSpan = 0.0;
        double meanY = 0.0;
        double lower = 0.0;
        double upper = 0.0;
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
            horizontalSpan += metrics.horizontalSpan();
            meanY += metrics.meanY();
            lower += metrics.lowerCrownShare();
            upper += metrics.upperCrownShare();
        }
        return new Summary(
            leaves / SAMPLES,
            height / SAMPLES,
            horizontalSpan / SAMPLES,
            meanY / SAMPLES,
            lower / SAMPLES,
            upper / SAMPLES
        );
    }

    private record Summary(
        double meanLeafCount,
        double meanHeight,
        double meanHorizontalSpan,
        double meanY,
        double meanLowerCrownShare,
        double meanUpperCrownShare
    ) {
    }

    private record Entry(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        Summary summary
    ) {
    }

    private record Export(
        int format,
        String generator,
        long rootSeed,
        int samplesPerEntry,
        TreeEnvironment environment,
        List<Entry> entries
    ) {
    }
}
