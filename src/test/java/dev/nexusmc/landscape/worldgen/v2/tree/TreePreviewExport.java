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
 * Exports deterministic tree models and their anatomical decisions for visual
 * tooling without loading Minecraft. Files are CI artifacts, not runtime
 * resources.
 */
public final class TreePreviewExport {
    private static final int FORMAT_VERSION = 8;
    private static final String GENERATOR_ID =
        "nexus_landscape:conifer_species_materials_v8";
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
        for (ConiferSpeciesProfile species : ConiferSpeciesProfile.values()) {
            for (TreeQualityTier quality : TreeQualityTier.values()) {
                int environmentIndex = 0;
                for (Map.Entry<String, TreeEnvironment> entry : environments.entrySet()) {
                    long seed = TreeLifeHistory.mix(
                        rootSeed
                            ^ ((long)species.ordinal() << 56)
                            ^ ((long)quality.ordinal() << 48)
                            ^ environmentIndex * 0x9E3779B97F4A7C15L
                    );
                    TreeEnvironment environment = entry.getValue();
                    ProceduralTreePlan procedural = new ProceduralTreePlan(
                        seed,
                        species,
                        quality,
                        environment,
                        quality == TreeQualityTier.HERO
                    );
                    TreeGenerationPipeline.GeneratedTree generated =
                        TreeGenerationPipeline.generate(procedural);
                    AnatomyPlan anatomy = generated.anatomy();
                    BranchGraph baseGraph = generated.baseGraph();
                    WindDeformationPlan wind = generated.wind();
                    BranchGraph graph = generated.graph();
                    CanopyPlan canopy = generated.canopy();
                    VoxelTreeModel model = generated.model();
                    CrownBiasMetrics crownBias = CrownBiasMetrics.measure(
                        model, environment
                    );
                    CrownShapeMetrics crownShape = CrownShapeMetrics.measure(model);
                    TreePlacementEnvelope envelope = TreePlacementEnvelope.estimate(
                        species, quality, environment, quality == TreeQualityTier.HERO
                    );
                    RoleCounts roles = roleCounts(graph);
                    TopShift topShift = topShift(baseGraph, graph);

                    Preview preview = new Preview(
                        FORMAT_VERSION,
                        GENERATOR_ID,
                        species,
                        species.materialProfile(),
                        entry.getKey(),
                        seed,
                        quality,
                        environment,
                        anatomy.history(),
                        anatomy.trunk(),
                        anatomy.roots(),
                        anatomy.branchFamilies(),
                        wind,
                        canopy,
                        crownBias,
                        crownShape,
                        envelope,
                        baseGraph.fingerprint(),
                        graph.fingerprint(),
                        model.fingerprint(),
                        topShift,
                        graph.segments().size(),
                        roles,
                        model.wood().size(),
                        undergroundWood(model),
                        model.leaves().size(),
                        bounds(model),
                        graph.segments(),
                        model.wood(),
                        model.leaves()
                    );
                    String name = species.name().toLowerCase()
                        + "_" + quality.name().toLowerCase()
                        + "_" + entry.getKey() + ".json";
                    Files.writeString(output.resolve(name), GSON.toJson(preview));
                    environmentIndex++;
                    exported++;
                }
            }
        }
        Files.writeString(
            output.resolve("manifest.json"),
            GSON.toJson(Map.of(
                "format", FORMAT_VERSION,
                "generator", GENERATOR_ID,
                "files", exported,
                "matrix", Map.of(
                    "species", ConiferSpeciesProfile.values().length,
                    "qualityTiers", TreeQualityTier.values().length,
                    "environments", environments.size()
                )
            ))
        );
        System.out.println(
            "TreePreviewExport: PASS files=" + exported
                + " output=" + output.toAbsolutePath()
        );
    }

    private static RoleCounts roleCounts(BranchGraph graph) {
        int trunk = 0;
        int roots = 0;
        int live = 0;
        int dead = 0;
        int leaders = 0;
        for (BranchGraph.Segment segment : graph.segments()) {
            switch (segment.role()) {
                case TRUNK -> trunk++;
                case ROOT -> roots++;
                case LIVE_BRANCH -> live++;
                case DEAD_BRANCH -> dead++;
                case SECONDARY_LEADER -> leaders++;
            }
        }
        return new RoleCounts(trunk, roots, live, dead, leaders);
    }

    private static TopShift topShift(BranchGraph before, BranchGraph after) {
        BranchGraph.Segment baseTop = highestSegment(before);
        BranchGraph.Segment movedTop = after.segments().stream()
            .filter(segment -> segment.id() == baseTop.id())
            .findFirst()
            .orElseThrow();
        double dx = movedTop.endX() - baseTop.endX();
        double dz = movedTop.endZ() - baseTop.endZ();
        return new TopShift(dx, dz, Math.hypot(dx, dz));
    }

    private static BranchGraph.Segment highestSegment(BranchGraph graph) {
        BranchGraph.Segment highest = graph.segments().getFirst();
        for (BranchGraph.Segment segment : graph.segments()) {
            if (segment.endY() > highest.endY()) {
                highest = segment;
            }
        }
        return highest;
    }

    private static int undergroundWood(VoxelTreeModel model) {
        return (int)model.wood().stream()
            .filter(voxel -> voxel.y() < 0)
            .count();
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
        int format,
        String generator,
        ConiferSpeciesProfile species,
        TreeMaterialProfile materialProfile,
        String environmentName,
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory lifeHistory,
        TrunkPlan trunkPlan,
        RootPlan rootPlan,
        BranchFamilyPlan branchFamilyPlan,
        WindDeformationPlan windDeformation,
        CanopyPlan canopyPlan,
        CrownBiasMetrics crownBias,
        CrownShapeMetrics crownShape,
        TreePlacementEnvelope placementEnvelope,
        long baseBranchFingerprint,
        long deformedBranchFingerprint,
        long voxelFingerprint,
        TopShift topShift,
        int branchSegments,
        RoleCounts segmentRoles,
        int woodBlocks,
        int undergroundWoodBlocks,
        int leafBlocks,
        Bounds bounds,
        List<BranchGraph.Segment> branches,
        List<VoxelTreeModel.Voxel> wood,
        List<VoxelTreeModel.Voxel> leaves
    ) {
    }

    private record RoleCounts(
        int trunkSegments,
        int rootSegments,
        int liveBranchSegments,
        int deadBranchSegments,
        int secondaryLeaderSegments
    ) {
    }

    private record TopShift(double x, double z, double distance) {
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
