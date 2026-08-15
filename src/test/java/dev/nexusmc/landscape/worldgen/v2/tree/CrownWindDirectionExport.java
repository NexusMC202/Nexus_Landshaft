package dev.nexusmc.landscape.worldgen.v2.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Verifies and exports aggregate crown response for opposing wind directions. */
public final class CrownWindDirectionExport {
    private static final int FORMAT_VERSION = 1;
    private static final int SAMPLES = 8;
    private static final long ROOT_SEED = 0x57494E444449524CL;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CrownWindDirectionExport() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
            ? Path.of("build", "tree-previews")
            : Path.of(args[0]);
        Files.createDirectories(output);

        TreeEnvironment windA = environment(-0.92, 0.24);
        TreeEnvironment windB = environment(0.92, -0.24);
        List<Entry> entries = new ArrayList<>();
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                CrownWindResponseSummary responseA = measure(species, quality, windA);
                CrownWindResponseSummary responseB = measure(species, quality, windB);
                requireDirectionalResponse(species, quality, "wind_a", responseA);
                requireDirectionalResponse(species, quality, "wind_b", responseB);
                entries.add(new Entry(
                    species,
                    quality,
                    windA,
                    responseA,
                    windB,
                    responseB
                ));
            }
        }

        Export export = new Export(
            FORMAT_VERSION,
            "nexus_landscape:crown_wind_direction_v1",
            ROOT_SEED,
            SAMPLES,
            List.copyOf(entries)
        );
        Files.writeString(
            output.resolve("crown-wind-direction.json"),
            GSON.toJson(export)
        );
        System.out.println(
            "CrownWindDirectionExport: PASS entries=" + entries.size()
                + " output=" + output.toAbsolutePath()
        );
    }

    private static TreeEnvironment environment(double windX, double windZ) {
        return new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            windX, windZ, 0.68, -0.12, 138
        );
    }

    private static CrownWindResponseSummary measure(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        return CrownWindResponseSummary.measure(
            ROOT_SEED,
            SAMPLES,
            species,
            quality,
            environment
        );
    }

    private static void requireDirectionalResponse(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        String wind,
        CrownWindResponseSummary response
    ) {
        String label = species + " " + quality + " " + wind;
        if (response.meanWindProjection() <= 0.0) {
            throw new AssertionError(label + " crown did not move leeward");
        }
        if (response.leewardLeaves() <= response.windwardLeaves()) {
            throw new AssertionError(label + " foliage is not leeward-biased");
        }
    }

    private record Entry(
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment windA,
        CrownWindResponseSummary responseA,
        TreeEnvironment windB,
        CrownWindResponseSummary responseB
    ) {
    }

    private record Export(
        int format,
        String generator,
        long rootSeed,
        int samplesPerDirection,
        List<Entry> entries
    ) {
    }
}
