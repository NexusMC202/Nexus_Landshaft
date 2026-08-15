package dev.nexusmc.landscape.worldgen.v2.tree;

/**
 * Complete immutable anatomical decision for one procedural conifer. All
 * expensive planning happens once before the branch graph is emitted.
 */
public record AnatomyPlan(
    long seed,
    ConiferSpeciesProfile species,
    TreeQualityTier quality,
    TreeEnvironment environment,
    TreeLifeHistory history,
    TrunkPlan trunk,
    RootPlan roots,
    BranchFamilyPlan branchFamilies
) {
    public AnatomyPlan {
        if (species == null || quality == null || environment == null
            || history == null || trunk == null || roots == null
            || branchFamilies == null) {
            throw new IllegalArgumentException("complete anatomy plan is required");
        }
    }

    public static AnatomyPlan resolve(ProceduralTreePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("procedural tree plan is required");
        }
        return resolve(
            plan.seed(),
            plan.species(),
            plan.quality(),
            plan.environment()
        );
    }

    public static AnatomyPlan resolve(
        long seed,
        ConiferSpeciesProfile species,
        TreeQualityTier quality,
        TreeEnvironment environment
    ) {
        if (species == null || quality == null || environment == null) {
            throw new IllegalArgumentException("anatomy inputs are required");
        }
        TreeLifeHistory history = TreeLifeHistory.generate(
            seed, quality, environment
        );
        ConiferSpeciesAnatomy speciesAnatomy = ConiferSpeciesAnatomy.resolve(
            seed, species, quality, environment, history
        );
        return new AnatomyPlan(
            seed,
            species,
            quality,
            environment,
            history,
            speciesAnatomy.trunk(),
            speciesAnatomy.roots(),
            speciesAnatomy.branches()
        );
    }

    public CanopyPlan canopy(BranchGraph graph) {
        return CanopyPlan.resolve(graph, this);
    }

    public long fingerprint() {
        long hash = seed ^ ((long)species.ordinal() << 61)
            ^ ((long)quality.ordinal() << 57);
        hash = mix(hash, history.ageYears());
        hash = mix(hash, Double.doubleToLongBits(history.vigor()));
        hash = mix(hash, trunk.height());
        hash = mix(hash, trunk.sections());
        hash = mix(hash, Double.doubleToLongBits(trunk.baseRadius()));
        hash = mix(hash, roots.arms().size());
        for (BranchFamilyPlan.Family family : branchFamilies.families()) {
            hash = mix(hash, family.kind().ordinal());
            hash = mix(hash, family.targetCount());
            hash = mix(hash, Double.doubleToLongBits(family.baseLength()));
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        hash ^= value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        return hash;
    }
}
