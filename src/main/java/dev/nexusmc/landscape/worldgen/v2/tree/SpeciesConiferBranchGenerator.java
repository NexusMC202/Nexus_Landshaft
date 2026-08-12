package dev.nexusmc.landscape.worldgen.v2.tree;

/** Species-aware entry point over the shared deterministic conifer engine. */
public final class SpeciesConiferBranchGenerator {
    private SpeciesConiferBranchGenerator() {
    }

    public static BranchGraph generate(
        long seed,
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history
    ) {
        if (species == null || quality == null || environment == null
            || history == null) {
            throw new IllegalArgumentException("species generation inputs are required");
        }
        ConiferSpeciesAnatomy speciesAnatomy = ConiferSpeciesAnatomy.resolve(
            seed, species, quality, environment, history
        );
        AnatomyPlan anatomy = new AnatomyPlan(
            seed,
            species,
            quality,
            environment,
            history,
            speciesAnatomy.trunk(),
            speciesAnatomy.roots(),
            speciesAnatomy.branches()
        );
        return ConiferBranchGenerator.generate(anatomy);
    }

    public static BranchGraph generate(AnatomyPlan anatomy) {
        return ConiferBranchGenerator.generate(anatomy);
    }
}
