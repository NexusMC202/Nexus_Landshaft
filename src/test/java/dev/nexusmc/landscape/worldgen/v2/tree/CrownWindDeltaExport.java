package dev.nexusmc.landscape.worldgen.v2.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports and verifies paired calm-versus-windy crown morphology. */
public final class CrownWindDeltaExport {
    private static final int FORMAT_VERSION = 1;
    private static final int SAMPLES = 8;
    private static final long ROOT_SEED = 0x4C454557415244L;
    private static final double MIN_PROJECTION_DELTA = 0.50;
    private static final double MIN_BALANCE_DELTA = 0.20;
    private static final double MIN_LEEWARD_SHARE_DELTA = 0.10;
    private static final long MIN_PINE_FOLIAGE_RETENTION_ADVANTAGE = 40L;
    private static final double MIN_HERO_PINE_PROJECTION_ADVANTAGE = 0.20;
    private static final double MIN_HERO_PINE_BALANCE_ADVANTAGE = 0.20;
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
                SampleSummary calmSummary = measure(species, quality, calm, windy);
                SampleSummary windySummary = measure(species, quality, windy, windy);
                Delta delta = new Delta(
                    windySummary.leafCount() - calmSummary.leafCount(),
                    windySummary.meanWindProjection() - calmSummary.meanWindProjection(),
                    windySummary.meanDirectionalBalance() - calmSummary.meanDirectionalBalance(),
                    windySummary.leewardShare() - calmSummary.leewardShare()
                );
                verifyDelta(species, quality, delta);
                entries.add(new Entry(species, quality, calmSummary, windySummary, delta));
            }
        }
        verifySpeciesIdentity(entries);

        Files.writeString(
            output.resolve("crown-wind-delta.json"),
            GSON.toJson(new Export(
                FORMAT_VERSION,
                "nexus_landscape:crown_wind_delta_v1",
                ROOT_SEED,
                SAMPLES,
                calm,
                windy,
                List.copyOf(entries)
            ))
        );
        System.out.println(
            "CrownWindDeltaExport: PASS entries=" + entries.size()
                + " output=" + output.toAbsolutePath()
        );
    }

    private static void verifyDelta(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        Delta delta
    ) {
        String label = species + " " + quality;
        require(delta.meanWindProjection() > MIN_PROJECTION_DELTA,
            label + " crown wind projection delta too small: " + delta.meanWindProjection());
        require(delta.meanDirectionalBalance() > MIN_BALANCE_DELTA,
            label + " crown directional balance delta too small: "
                + delta.meanDirectionalBalance());
        require(delta.leewardShare() > MIN_LEEWARD_SHARE_DELTA,
            label + " crown leeward-share delta too small: " + delta.leewardShare());
    }

    private static void verifySpeciesIdentity(List<Entry> entries) {
        for (TreeQualityTier quality : TreeQualityTier.values()) {
            Entry spruce = find(entries, ConiferSpeciesProfile.SPRUCE, quality);
            Entry pine = find(entries, ConiferSpeciesProfile.PINE, quality);
            long foliageRetentionAdvantage = pine.windyMinusCalm().leafCount()
                - spruce.windyMinusCalm().leafCount();
            require(foliageRetentionAdvantage > MIN_PINE_FOLIAGE_RETENTION_ADVANTAGE,
                quality + " pine no longer retains distinctly more foliage than spruce: "
                    + foliageRetentionAdvantage);
        }

        Entry spruceHero = find(
            entries, ConiferSpeciesProfile.SPRUCE, TreeQualityTier.HERO
        );
        Entry pineHero = find(
            entries, ConiferSpeciesProfile.PINE, TreeQualityTier.HERO
        );
        double projectionAdvantage = pineHero.windyMinusCalm().meanWindProjection()
            - spruceHero.windyMinusCalm().meanWindProjection();
        double balanceAdvantage = pineHero.windyMinusCalm().meanDirectionalBalance()
            - spruceHero.windyMinusCalm().meanDirectionalBalance();
        require(projectionAdvantage > MIN_HERO_PINE_PROJECTION_ADVANTAGE,
            "HERO pine projection response is no longer distinct from spruce: "
                + projectionAdvantage);
        require(balanceAdvantage > MIN_HERO_PINE_BALANCE_ADVANTAGE,
            "HERO pine directional response is no longer distinct from spruce: "
                + balanceAdvantage);
    }

    private static Entry find(
        List<Entry> entries,
        ConiferSpeciesProfile species,
        TreeQualityTier quality
    ) {
        for (Entry entry : entries) {
            if (entry.species() == species && entry.quality() == quality) {
                return entry;
            }
        }
        throw new AssertionError("missing crown delta entry: " + species + " " + quality);
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
                seed, species, quality, growthEnvironment,
                quality == TreeQualityTier.HERO
            );
            CrownBiasMetrics metrics = CrownBiasMetrics.measure(
                TreeGenerationPipeline.generate(plan).model(),
                measurementEnvironment
            );
            leaves += metrics.leafCount();
            leeward += metrics.leewardLeaves();
            windward += metrics.windwardLeaves();
            neutral += metrics.neutralLeaves();
            projection += metrics.windProjection();
            balance += metrics.directionalBalance();
        }
        return new SampleSummary(
            leaves, leeward, windward, neutral,
            projection / SAMPLES, balance / SAMPLES
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
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
