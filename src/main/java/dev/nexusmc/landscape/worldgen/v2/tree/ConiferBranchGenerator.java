package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic conifer skeleton generator driven by explicit anatomical
 * plans. The complete anatomy is resolved before any graph segment is emitted.
 */
public final class ConiferBranchGenerator {
    private static final double GOLDEN_ANGLE = 2.399963229728653;

    private ConiferBranchGenerator() {
    }

    /** Compatibility path for older pure callers using generic conifer anatomy. */
    public static BranchGraph generate(
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history
    ) {
        if (quality == null || environment == null || history == null) {
            throw new IllegalArgumentException("conifer generation inputs are required");
        }
        TrunkPlan trunk = TrunkPlan.resolve(seed, quality, environment, history);
        RootPlan roots = RootPlan.resolve(seed, quality, environment, history, trunk);
        BranchFamilyPlan branchFamilies = BranchFamilyPlan.resolve(
            seed, quality, environment, history, trunk
        );
        return generateFromPlans(
            seed, quality, environment, history, trunk, roots, branchFamilies
        );
    }

    /** Main optimized path: all anatomical planning has already happened once. */
    public static BranchGraph generate(AnatomyPlan anatomy) {
        if (anatomy == null) {
            throw new IllegalArgumentException("anatomy plan is required");
        }
        return generateFromPlans(
            anatomy.seed(),
            anatomy.quality(),
            anatomy.environment(),
            anatomy.history(),
            anatomy.trunk(),
            anatomy.roots(),
            anatomy.branchFamilies()
        );
    }

    private static BranchGraph generateFromPlans(
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history,
        TrunkPlan trunk,
        RootPlan roots,
        BranchFamilyPlan branchFamilies
    ) {
        List<BranchGraph.Segment> segments = new ArrayList<>();
        int budget = quality.budget().maxBranchSegments();
        int nextId = appendTrunk(segments, trunk);
        nextId = appendRoots(segments, roots, trunk, nextId, budget);
        nextId = appendBranchFamilies(
            segments,
            branchFamilies,
            trunk,
            environment,
            history,
            seed,
            quality,
            nextId,
            budget
        );
        appendSecondaryLeader(
            segments, trunk, environment, history, nextId, budget
        );
        return new BranchGraph(segments);
    }

    private static int appendTrunk(
        List<BranchGraph.Segment> segments,
        TrunkPlan trunk
    ) {
        int previous = -1;
        double startX = 0.0;
        double startY = 0.0;
        double startZ = 0.0;
        for (int section = 0; section < trunk.sections(); section++) {
            double t0 = section / (double)trunk.sections();
            double t1 = (section + 1.0) / trunk.sections();
            double endX = trunk.leanX() * t1 * t1;
            double endY = trunk.height() * t1;
            double endZ = trunk.leanZ() * t1 * t1;
            int id = segments.size();
            segments.add(new BranchGraph.Segment(
                id,
                previous,
                startX,
                startY,
                startZ,
                endX,
                endY,
                endZ,
                trunk.radiusAt(t0),
                trunk.radiusAt(t1),
                SegmentRole.TRUNK
            ));
            previous = id;
            startX = endX;
            startY = endY;
            startZ = endZ;
        }
        return segments.size();
    }

    private static int appendRoots(
        List<BranchGraph.Segment> segments,
        RootPlan roots,
        TrunkPlan trunk,
        int nextId,
        int budget
    ) {
        for (RootPlan.RootArm root : roots.arms()) {
            if (nextId >= budget) {
                break;
            }
            double startRadius = Math.min(
                trunk.baseRadius(),
                root.startRadius() + roots.buttressRadius()
            );
            segments.add(new BranchGraph.Segment(
                nextId,
                0,
                0.0,
                0.10,
                0.0,
                root.endX(),
                root.endY(),
                root.endZ(),
                startRadius,
                root.endRadius(),
                SegmentRole.ROOT
            ));
            nextId++;
        }
        return nextId;
    }

    private static int appendBranchFamilies(
        List<BranchGraph.Segment> segments,
        BranchFamilyPlan plan,
        TrunkPlan trunk,
        TreeEnvironment environment,
        TreeLifeHistory history,
        long seed,
        TreeQualityTier quality,
        int nextId,
        int budget
    ) {
        long state = TreeLifeHistory.mix(seed ^ 0x414E41544F4D594CL);
        int familyOrdinal = 0;
        for (BranchFamilyPlan.Family family : plan.families()) {
            for (int index = 0;
                 index < family.targetCount() && nextId < budget;
                 index++) {
                state = TreeLifeHistory.mix(state ^ nextId);
                double vertical = family.verticalAt(index, seed);
                double lossRoll = TreeLifeHistory.unit(state);
                if (family.foliageBearing()
                    && lossRoll < history.crownLoss() * 0.30) {
                    continue;
                }

                double countDivisor = Math.max(1.0, family.targetCount());
                double angle = family.phase()
                    + index * GOLDEN_ANGLE
                    + familyOrdinal * 0.43
                    + (TreeLifeHistory.unit(TreeLifeHistory.mix(state)) - 0.5) * 0.46;
                double directionX = Math.cos(angle);
                double directionZ = Math.sin(angle);
                double bandPosition = normalizedBandPosition(family, vertical);
                double familyShape = familyLengthShape(family.kind(), bandPosition);
                double length = family.baseLength()
                    * familyShape
                    * (0.78 + TreeLifeHistory.unit(state) * 0.42)
                    * (0.80 + history.vigor() * 0.25);

                double openProjection = environment.openSpaceX() * directionX
                    + environment.openSpaceZ() * directionZ;
                length *= WindAsymmetryPolicy.branchLengthMultiplier(
                    environment, directionX, directionZ
                );
                length *= clamp(1.0 + openProjection * 0.12, 0.84, 1.14);

                double attachX = trunk.leanX() * vertical * vertical;
                double attachY = trunk.height() * vertical;
                double attachZ = trunk.leanZ() * vertical * vertical;
                double radialX = directionX * length;
                double radialZ = directionZ * length;
                double verticalOffset = length * family.upwardLift()
                    - length * family.droop() * (0.72 + vertical * 0.38);

                double windDamageBonus = WindAsymmetryPolicy.damageProbabilityBonus(
                    environment, directionX, directionZ
                );
                double deadProbability = clamp(
                    family.deadProbability() + windDamageBonus,
                    0.0,
                    0.88
                );
                boolean dead = !family.foliageBearing()
                    || TreeLifeHistory.unit(
                        TreeLifeHistory.mix(state ^ 0x444541444252414EL)
                    ) < deadProbability;
                SegmentRole role = dead
                    ? SegmentRole.DEAD_BRANCH
                    : SegmentRole.LIVE_BRANCH;
                double startRadius = Math.max(
                    0.20,
                    trunk.radiusAt(vertical) * branchThickness(family.kind())
                );
                double endRadius = dead
                    ? 0.0
                    : Math.max(0.07, startRadius * 0.24);

                segments.add(new BranchGraph.Segment(
                    nextId,
                    trunkParentFor(vertical, trunk.sections()),
                    attachX,
                    attachY,
                    attachZ,
                    attachX + radialX,
                    attachY + verticalOffset,
                    attachZ + radialZ,
                    startRadius,
                    endRadius,
                    role
                ));
                nextId++;

                if (shouldAddSecondaryBranch(
                    family, quality, index, countDivisor, state
                ) && nextId < budget) {
                    double sideAngle = angle
                        + (TreeLifeHistory.unit(TreeLifeHistory.mix(state ^ 91L)) > 0.5
                            ? 0.62 : -0.62);
                    double secondaryLength = length * 0.42;
                    double branchEndX = attachX + radialX;
                    double branchEndY = attachY + verticalOffset;
                    double branchEndZ = attachZ + radialZ;
                    segments.add(new BranchGraph.Segment(
                        nextId,
                        nextId - 1,
                        branchEndX,
                        branchEndY,
                        branchEndZ,
                        branchEndX + Math.cos(sideAngle) * secondaryLength,
                        branchEndY - secondaryLength * family.droop() * 0.45,
                        branchEndZ + Math.sin(sideAngle) * secondaryLength,
                        Math.max(0.10, startRadius * 0.32),
                        dead ? 0.0 : 0.05,
                        role
                    ));
                    nextId++;
                }
            }
            familyOrdinal++;
        }
        return nextId;
    }

    private static void appendSecondaryLeader(
        List<BranchGraph.Segment> segments,
        TrunkPlan trunk,
        TreeEnvironment environment,
        TreeLifeHistory history,
        int nextId,
        int budget
    ) {
        if (!trunk.secondaryLeader() || nextId >= budget) {
            return;
        }
        double split = trunk.secondaryLeaderStart();
        double startX = trunk.leanX() * split * split;
        double startY = trunk.height() * split;
        double startZ = trunk.leanZ() * split * split;
        double direction = history.trunkLean() >= 0.0 ? -1.0 : 1.0;
        double endVertical = clamp(
            0.90 - history.leaderDamage() * 0.10,
            split + 0.12,
            0.94
        );
        segments.add(new BranchGraph.Segment(
            nextId,
            trunkParentFor(split, trunk.sections()),
            startX,
            startY,
            startZ,
            startX + direction * trunk.height() * 0.12,
            trunk.height() * endVertical,
            startZ + environment.openSpaceZ() * trunk.height() * 0.07,
            Math.max(0.24, trunk.radiusAt(split) * 0.60),
            0.10,
            SegmentRole.SECONDARY_LEADER
        ));
    }

    private static double normalizedBandPosition(
        BranchFamilyPlan.Family family,
        double vertical
    ) {
        return clamp(
            (vertical - family.minVertical())
                / (family.maxVertical() - family.minVertical()),
            0.0,
            1.0
        );
    }

    private static double familyLengthShape(
        BranchFamilyPlan.Kind kind,
        double t
    ) {
        return switch (kind) {
            case LOWER_SPARSE -> 0.78 + (1.0 - t) * 0.20;
            case MIDDLE_STRUCTURAL -> 0.78 + Math.sin(t * Math.PI) * 0.28;
            case UPPER_SHORT -> 1.0 - t * 0.34;
            case DEAD_AND_DAMAGED -> 0.72 + (1.0 - t) * 0.16;
        };
    }

    private static double branchThickness(BranchFamilyPlan.Kind kind) {
        return switch (kind) {
            case LOWER_SPARSE -> 0.40;
            case MIDDLE_STRUCTURAL -> 0.46;
            case UPPER_SHORT -> 0.34;
            case DEAD_AND_DAMAGED -> 0.30;
        };
    }

    private static boolean shouldAddSecondaryBranch(
        BranchFamilyPlan.Family family,
        TreeQualityTier quality,
        int index,
        double countDivisor,
        long state
    ) {
        if (family.kind() != BranchFamilyPlan.Kind.MIDDLE_STRUCTURAL
            || quality == TreeQualityTier.BASIC) {
            return false;
        }
        double chance = quality == TreeQualityTier.HERO ? 0.52 : 0.26;
        chance *= 0.90 + index / countDivisor * 0.20;
        return TreeLifeHistory.unit(TreeLifeHistory.mix(state ^ 0x5345434F4E444152L))
            < chance;
    }

    private static int trunkParentFor(double vertical, int trunkSections) {
        int index = (int)Math.floor(vertical * trunkSections);
        return Math.max(0, Math.min(trunkSections - 1, index));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
