package dev.nexusmc.landscape.worldgen.v2.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports paired calm-versus-windy crown morphology using identical seeds. */
public final class CrownWindDeltaExport {
    private static final int FORMAT_VERSION = 1;
    private static final int SAMPLES = 8;
    private static final long ROOT_SEED = 0x4C454557415244L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CrownWindDeltaExport() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
            ? Path.of("build", "tree-previews")
            : Path.of(args[0]);
        Files.createDirectories(output);

        TreeEnvironment windy = windyEnvironment();
        TreeEnvironment calm = calmEnvironment();
        List<Entry> entries = new ArrayList<>();
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                SampleSummary calmMeasuredAlongWind = measure(
                    species, quality, calm, windy
                );
                SampleSummary windyMeasuredAlongWind = measure(
                    species, quality, windy, windy
                );
                entries.add(new Entry(
                    species,
                    quality,
                    calmMeasuredAlongWind,
                    windyMeasuredAlongWind,
                    new Delta(
                        windyMeasuredAlongWind.leafCount()
                            - calmMeasuredAlongWind.leafCount(),
                        windyMeasuredAlongWind.meanWindProjection()
                            - calmMeasuredAlongWind.meanWindProjection(),
                        windyMeasuredAlongWind.meanDirectionalBalance()
                            - calmMeasuredAlongWind.meanDirectionalBalance(),
                        windyMeasuredAlongWind.leewardShare()
                            - calmMeasuredAlongWind.leewardShare()
                    )
                ));
            }
        }

        Export export = new Export(
            FORMAT_VERSION,
            "nexus_landscape:crown_wind_delta_v1",
            ROOT_SEED,
            SAMPLES,
            calm,
            windy,
            List.copyOf(entries)
        );
        Files.writeString(
            output.resolve("crown-wind-delta.json"),
            GSON.toJson(export)
        );
        System.out.println(
            "CrownWindDeltaExport: PASS entries=" + entries.size()
                + " output=" + output.toAbsolutePath()
        );
    }

    private static SampleSummary measure(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment growthEnvironment,
        TreeEnvironment measurementEnvironment
    ) {
        long leaves = 0L;
        long leeward = 0L;
        long windward = 0L;
        long neutral = 0L;
        double projection = 0.0;
        double balance = 0.0;

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
                growthEnvironment,
                quality == TreeQualityTier.HERO
            );
            VoxelTreeModel model = TreeGenerationPipeline.generate(plan).model();
            CrownBiasMetrics metrics = CrownBiasMetrics.measure(
                model, measurementEnvironment
            );
            leaves += metrics.leafCount();
            leeward += metrics.leewardLeaves();
            windward += metrics.windwardLeaves();
            neutral += metrics.neutralLeaves();
            projection += metrics.windProjection();
            balance += metrics.directionalBalance();
        }

        return new SampleSummary(
            leaves,
            leeward,
            windward,
            neutral,
            projection / SAMPLES,
            balance / SAMPLES
        );
    }

    private static TreeEnvironment windyEnvironment() {
        return new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            -0.92, 0.24, 0.68, -0.12, 138
        );
    }

    private static TreeEnvironment calmEnvironment() {
        return new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            0.0, 0.0, 0.68, -0.12, 138
        );
    }

    private record SampleSummary(
        long leafCount,
        long leewardLeaves,
        long windwardLeaves,
        long neutralLeaves,
        double meanWindProjection,
        double meanDirectionalBalance
    ) {
        private double leewardShare() {
            long side = leewardLeaves + windwardLeaves;
            return side == 0L ? 0.5 : leewardLeaves / (double)side;
        }
    }

    private record Delta(
        long leafCount,
        double meanWindProjection,
        double meanDirectionalBalance,
        double leewardShare
    ) {
    }

    private record Entry(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        SampleSummary calmMeasuredAlongWind,
        SampleSummary windyMeasuredAlongWind,
        Delta windyMinusCalm
    ) {
    }

    private record Export(
        int format,
        String generator,
        long rootSeed,
        int samplesPerEntry,
        TreeEnvironment calmGrowthEnvironment,
        TreeEnvironment windyGrowthEnvironment,
        List<Entry> entries
    ) {
    }
}
