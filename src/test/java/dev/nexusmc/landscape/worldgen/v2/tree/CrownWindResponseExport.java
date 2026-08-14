package dev.nexusmc.landscape.worldgen.v2.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports aggregate deterministic wind-response morphology for visual tooling. */
public final class CrownWindResponseExport {
    private static final int FORMAT_VERSION = 1;
    private static final int SAMPLES = 8;
    private static final long ROOT_SEED = 0x4C454557415244L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CrownWindResponseExport() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
            ? Path.of("build", "tree-previews")
            : Path.of(args[0]);
        Files.createDirectories(output);

        TreeEnvironment windy = new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            -0.92, 0.24, 0.68, -0.12, 138
        );
        List<Entry> entries = new ArrayList<>();
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                CrownWindResponseSummary summary = CrownWindResponseSummary.measure(
                    ROOT_SEED, SAMPLES, species, quality, windy
                );
                entries.add(new Entry(species, quality, summary));
            }
        }

        Export export = new Export(
            FORMAT_VERSION,
            "nexus_landscape:crown_wind_response_v1",
            ROOT_SEED,
            SAMPLES,
            windy,
            List.copyOf(entries)
        );
        Files.writeString(
            output.resolve("crown-wind-response.json"),
            GSON.toJson(export)
        );
        CrownWindDeltaExport.main(new String[]{output.toString()});
        CrownShapeMetricsSelfTest.main(new String[0]);
        System.out.println(
            "CrownWindResponseExport: PASS entries=" + entries.size()
                + " output=" + output.toAbsolutePath()
        );
    }

    private record Entry(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        CrownWindResponseSummary summary
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
