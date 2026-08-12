package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Single pure deterministic construction path for a complete procedural tree.
 * Runtime placement, caching, previews and tests should share this pipeline so
 * the same plan cannot produce different geometry depending on the caller.
 */
public final class TreeGenerationPipeline {
    private TreeGenerationPipeline() {
    }

    public static GeneratedTree generate(ProceduralTreePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("tree plan is required");
        }
        return generate(
            plan.seed(),
            plan.species(),
            plan.quality(),
            plan.environment()
        );
    }

    public static GeneratedTree generate(
        long seed,
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        if (species == null || quality == null || environment == null) {
            throw new IllegalArgumentException("tree generation inputs are required");
        }
        AnatomyPlan anatomy = AnatomyPlan.resolve(
            seed,
            species,
            quality,
            environment
        );
        BranchGraph baseGraph = SpeciesConiferBranchGenerator.generate(anatomy);
        WindDeformationPlan wind = WindDeformationPlan.resolve(anatomy);
        BranchGraph graph = wind.apply(baseGraph, anatomy);
        CanopyPlan canopy = anatomy.canopy(graph);
        VoxelTreeModel model = TreeVoxelizer.voxelize(graph, quality, canopy);
        return new GeneratedTree(
            anatomy,
            baseGraph,
            wind,
            graph,
            canopy,
            model
        );
    }

    public record GeneratedTree(
        AnatomyPlan anatomy,
        BranchGraph baseGraph,
        WindDeformationPlan wind,
        BranchGraph graph,
        CanopyPlan canopy,
        VoxelTreeModel model
    ) {
        public GeneratedTree {
            if (anatomy == null || baseGraph == null || wind == null
                || graph == null || canopy == null || model == null) {
                throw new IllegalArgumentException(
                    "generated tree stages are required"
                );
            }
            if (baseGraph.segments().size() != graph.segments().size()) {
                throw new IllegalArgumentException(
                    "wind deformation changed tree topology"
                );
            }
            if (model.quality() != anatomy.quality()) {
                throw new IllegalArgumentException(
                    "generated model quality does not match anatomy"
                );
            }
        }
    }
}
