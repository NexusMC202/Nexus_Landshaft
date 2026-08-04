package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.List;

/** Species-aware layer over the shared deterministic conifer engine. */
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
        if (species == null) {
            throw new IllegalArgumentException("conifer species is required");
        }
        BranchGraph base = ConiferBranchGenerator.generate(
            seed, quality, environment, history
        );
        TrunkPlan baseTrunk = TrunkPlan.resolve(
            seed, quality, environment, history
        );
        ConiferSpeciesAnatomy anatomy = ConiferSpeciesAnatomy.resolve(
            seed, species, quality, environment, history
        );
        TrunkPlan targetTrunk = anatomy.trunk();

        List<BranchGraph.Segment> transformed = new ArrayList<>(
            base.segments().size()
        );
        for (BranchGraph.Segment segment : base.segments()) {
            boolean trunk = segment.id() < baseTrunk.sections();
            boolean root = !trunk
                && segment.startY() <= 0.15
                && segment.endY() < 0.0;
            transformed.add(transform(
                segment, species, baseTrunk, targetTrunk, trunk, root
            ));
        }
        return new BranchGraph(transformed);
    }

    private static BranchGraph.Segment transform(
        BranchGraph.Segment segment,
        ConiferSpeciesProfile species,
        TrunkPlan base,
        TrunkPlan target,
        boolean trunk,
        boolean root
    ) {
        double startX;
        double startY;
        double startZ;
        double endX;
        double endY;
        double endZ;
        double radiusScale;

        if (trunk) {
            startX = segment.startX() * species.heightMultiplier();
            startY = scaleTrunkY(segment.startY(), base, target);
            startZ = segment.startZ() * species.heightMultiplier();
            endX = segment.endX() * species.heightMultiplier();
            endY = scaleTrunkY(segment.endY(), base, target);
            endZ = segment.endZ() * species.heightMultiplier();
            radiusScale = species.trunkRadiusMultiplier();
        } else if (root) {
            startX = segment.startX();
            startY = segment.startY();
            startZ = segment.startZ();
            endX = segment.endX() * species.trunkRadiusMultiplier();
            endY = segment.endY();
            endZ = segment.endZ() * species.trunkRadiusMultiplier();
            radiusScale = species.trunkRadiusMultiplier();
        } else {
            double startVertical = clamp(
                segment.startY() / Math.max(1.0, base.height()), 0.0, 1.0
            );
            double endVertical = clamp(
                segment.endY() / Math.max(1.0, base.height()), 0.0, 1.0
            );
            double shiftedStart = shiftedVertical(startVertical, species);
            double shiftedEnd = shiftedVertical(endVertical, species);
            double radialScale = species.branchLengthMultiplier();
            startX = segment.startX() * radialScale;
            startY = target.height() * shiftedStart;
            startZ = segment.startZ() * radialScale;
            endX = segment.endX() * radialScale;
            endY = target.height() * shiftedEnd;
            endZ = segment.endZ() * radialScale;
            radiusScale = Math.sqrt(
                species.trunkRadiusMultiplier()
                    * species.branchLengthMultiplier()
            );
        }

        return new BranchGraph.Segment(
            segment.id(),
            segment.parentId(),
            startX,
            startY,
            startZ,
            endX,
            endY,
            endZ,
            segment.startRadius() * radiusScale,
            segment.endRadius() * radiusScale,
            segment.dead()
        );
    }

    private static double scaleTrunkY(
        double y,
        TrunkPlan base,
        TrunkPlan target
    ) {
        return y / Math.max(1.0, base.height()) * target.height();
    }

    private static double shiftedVertical(
        double vertical,
        ConiferSpeciesProfile species
    ) {
        double offset = species.crownStartOffset();
        if (offset >= 0.0) {
            return clamp(vertical + offset * (1.0 - vertical), 0.0, 1.0);
        }
        return clamp(vertical + offset * vertical, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
