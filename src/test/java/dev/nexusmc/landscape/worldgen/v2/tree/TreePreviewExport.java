package dev.nexusmc.landscape.worldgen.v2.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports deterministic tree models for visual tooling without loading
 * Minecraft. Files are CI artifacts, not runtime resources.
 */
public final class TreePreviewExport {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private TreePreviewExport() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
            ? Path.of("build", "tree-previews")
            : Path.of(args[0]);
        Files.createDirectories(output);

        Map<String, TreeEnvironment> environments = new LinkedHashMap<>();
        environments.put("open_lowland", new TreeEnvironment(
            0.08, 0.72, 0.10, 0.18,
            0.10, -0.08, 0.75, 0.20, 86
        ));
        environments.put("dense_forest", new TreeEnvironment(
            0.14, 0.64, 0.88, 0.10,
            -0.12, 0.10, 0.05, -0.05, 94
        ));
        environments.put("windy_slope", new TreeEnvironment(
            0.62, 0.38, 0.22, 0.04,
            -0.92, 0.24, 0.68, -0.12, 138
        ));

        long rootSeed = 0x4E45585553545245L;
        int exported = 0;
        for (TreeQualityTier quality : TreeQualityTier.values()) {
            int environmentIndex = 0;
            for (Map.Entry<String, TreeEnvironment> entry : environments.entrySet()) {
                long seed = TreeLifeHistory.mix(
                    rootSeed
                        ^ ((long)quality.ordinal() << 48)
                        ^ environmentIndex * 0x9E3779B97F4A7C15L
                );
                TreeEnvironment environment = entry.getValue();
                TreeLifeHistory history = TreeLifeHistory.generate(
                    seed, quality, environment
                );
                BranchGraph graph = ConiferBranchGenerator.generate(
                    seed, quality, environment, history
                );
                VoxelTreeModel model = TreeVoxelizer.voxelize(
                    seed, quality, graph
                );
                Preview preview = new Preview(
                    "nexus_landscape:conifer_v1",
                    entry.getKey(),
                    seed,
                    quality,
                    environment,
                    history,
                    graph.fingerprint(),
                    model.fingerprint(),
                    graph.segments().size(),
                    model.wood().size(),
                    model.leaves().size(),
                    bounds(model),
                    graph.segments(),
                    model.wood(),
                    model.leaves()
                );
                String name = quality.name().toLowerCase()
                    + "_" + entry.getKey() + ".json";
                Files.writeString(output.resolve(name), GSON.toJson(preview));
                environmentIndex++;
                exported++;
            }
        }
        Files.writeString(
            output.resolve("manifest.json"),
            GSON.toJson(Map.of(
                "format", 1,
                "generator", "nexus_landscape:conifer_v1",
                "files", exported
            ))
        );
        System.out.println(
            "TreePreviewExport: PASS files=" + exported
                + " output=" + output.toAbsolutePath()
        );
    }

    private static Bounds bounds(VoxelTreeModel model) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (VoxelTreeModel.Voxel voxel : combined(model)) {
            minX = Math.min(minX, voxel.x());
            minY = Math.min(minY, voxel.y());
            minZ = Math.min(minZ, voxel.z());
            maxX = Math.max(maxX, voxel.x());
            maxY = Math.max(maxY, voxel.y());
            maxZ = Math.max(maxZ, voxel.z());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<VoxelTreeModel.Voxel> combined(VoxelTreeModel model) {
        java.util.ArrayList<VoxelTreeModel.Voxel> result =
            new java.util.ArrayList<>(model.wood());
        result.addAll(model.leaves());
        return result;
    }

    private record Preview(
        String generator,
        String environmentName,
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory lifeHistory,
        long branchFingerprint,
        long voxelFingerprint,
        int branchSegments,
        int woodBlocks,
        int leafBlocks,
        Bounds bounds,
        List<BranchGraph.Segment> branches,
        List<VoxelTreeModel.Voxel> wood,
        List<VoxelTreeModel.Voxel> leaves
    ) {
    }

    private record Bounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
    }
}
