package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic conifer skeleton generator. It creates a dominant leader,
 * irregular branch whorls, wind-shaped asymmetry, crown gaps and optional
 * damage recovery without touching Minecraft world state.
 */
public final class ConiferBranchGenerator {
    private ConiferBranchGenerator() {
    }

    public static BranchGraph generate(
        long seed,
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history
    ) {
        List<BranchGraph.Segment> segments = new ArrayList<>();
        TreeQualityTier.TreeBudget budget = quality.budget();

        int targetHeight = targetHeight(quality, environment, history);
        int trunkSections = switch (quality) {
            case BASIC -> 3;
            case MID -> 5;
            case HERO -> 7;
        };
        trunkSections = Math.min(trunkSections, targetHeight);

        int id = 0;
        int previous = -1;
        double startX = 0.0;
        double startY = 0.0;
        double startZ = 0.0;
        for (int section = 0; section < trunkSections; section++) {
            double t0 = section / (double)trunkSections;
            double t1 = (section + 1.0) / trunkSections;
            double endY = targetHeight * t1;
            double bend = history.trunkLean() * targetHeight * 0.12;
            double endX = bend * t1 * t1 + environment.openSpaceX() * t1 * 0.35;
            double endZ = environment.windZ() * targetHeight * 0.035 * t1 * t1
                + environment.openSpaceZ() * t1 * 0.35;
            double baseRadius = radiusFor(quality, history);
            double startRadius = Math.max(0.32, baseRadius * (1.0 - t0 * 0.78));
            double endRadius = Math.max(0.18, baseRadius * (1.0 - t1 * 0.82));
            segments.add(new BranchGraph.Segment(
                id, previous,
                startX, startY, startZ,
                endX, endY, endZ,
                startRadius, endRadius,
                false
            ));
            previous = id;
            id++;
            startX = endX;
            startY = endY;
            startZ = endZ;
        }

        int branchBudget = budget.maxBranchSegments() - segments.size();
        int desiredWhorls = switch (quality) {
            case BASIC -> 4;
            case MID -> 8;
            case HERO -> 14;
        };
        desiredWhorls = Math.max(2, Math.min(desiredWhorls, branchBudget / 2));

        long state = TreeLifeHistory.mix(seed ^ 0x0C0A1F3E5D779B11L);
        for (int whorl = 0; whorl < desiredWhorls && id < budget.maxBranchSegments(); whorl++) {
            double vertical = (whorl + 1.0) / (desiredWhorls + 1.8);
            if (vertical < 0.20 || vertical > 0.91) {
                continue;
            }
            state = TreeLifeHistory.mix(state);
            double gapRoll = TreeLifeHistory.unit(state);
            if (gapRoll < history.crownLoss() * 0.42) {
                continue;
            }

            int branchesInWhorl = quality == TreeQualityTier.BASIC ? 2 : 3;
            if (quality == TreeQualityTier.HERO && TreeLifeHistory.unit(TreeLifeHistory.mix(state)) > 0.62) {
                branchesInWhorl = 4;
            }
            for (int branch = 0;
                 branch < branchesInWhorl && id < budget.maxBranchSegments();
                 branch++) {
                state = TreeLifeHistory.mix(state);
                double jitter = (TreeLifeHistory.unit(state) - 0.5) * 0.55;
                double angle = (Math.PI * 2.0 * branch / branchesInWhorl)
                    + whorl * 2.399963229728653
                    + jitter;
                double crownTaper = 1.0 - Math.abs(vertical - 0.46) / 0.54;
                crownTaper = Math.max(0.20, crownTaper);
                double baseLength = switch (quality) {
                    case BASIC -> 2.4;
                    case MID -> 4.2;
                    case HERO -> 6.8;
                };
                double length = baseLength * crownTaper
                    * (0.72 + TreeLifeHistory.unit(state) * 0.48)
                    * (0.78 + history.vigor() * 0.28);

                double windBias = environment.windX() * Math.cos(angle)
                    + environment.windZ() * Math.sin(angle);
                length *= clamp(1.0 + windBias * 0.28, 0.55, 1.30);

                double attachY = targetHeight * vertical;
                double attachX = history.trunkLean() * attachY * 0.08;
                double attachZ = environment.windZ() * attachY * 0.025;
                double droop = length * (0.10 + vertical * 0.18);
                double endX = attachX + Math.cos(angle) * length;
                double endZ = attachZ + Math.sin(angle) * length;
                double endY = attachY + length * (0.10 + vertical * 0.22) - droop;
                boolean dead = TreeLifeHistory.unit(TreeLifeHistory.mix(state ^ id))
                    < history.deadBranchShare();
                double startRadius = Math.max(0.22,
                    radiusFor(quality, history) * (0.38 - vertical * 0.16));
                double endRadius = dead ? 0.0 : Math.max(0.08, startRadius * 0.28);
                int parent = trunkParentFor(vertical, trunkSections);
                segments.add(new BranchGraph.Segment(
                    id, parent,
                    attachX, attachY, attachZ,
                    endX, endY, endZ,
                    startRadius, endRadius,
                    dead
                ));
                id++;
            }
        }

        if (history.secondaryLeader() && id < budget.maxBranchSegments()) {
            double splitY = targetHeight * 0.62;
            double direction = history.trunkLean() >= 0.0 ? -1.0 : 1.0;
            segments.add(new BranchGraph.Segment(
                id,
                trunkParentFor(0.62, trunkSections),
                history.trunkLean() * splitY * 0.08,
                splitY,
                environment.windZ() * splitY * 0.025,
                direction * targetHeight * 0.13,
                targetHeight * (0.88 - history.leaderDamage() * 0.12),
                environment.openSpaceZ() * targetHeight * 0.08,
                Math.max(0.28, radiusFor(quality, history) * 0.30),
                0.10,
                false
            ));
        }

        return new BranchGraph(segments);
    }

    private static int targetHeight(
        TreeQualityTier quality,
        TreeEnvironment environment,
        TreeLifeHistory history
    ) {
        double base = switch (quality) {
            case BASIC -> 8.0;
            case MID -> 15.0;
            case HERO -> 27.0;
        };
        double competitionStretch = 1.0 + environment.forestCompetition() * 0.18;
        double stressReduction = 1.0
            - environment.slope() * 0.10
            - history.leaderDamage() * 0.14;
        int height = (int)Math.round(base * competitionStretch * stressReduction);
        return Math.max(5, Math.min(quality.budget().maxHeight(), height));
    }

    private static double radiusFor(
        TreeQualityTier quality,
        TreeLifeHistory history
    ) {
        double base = switch (quality) {
            case BASIC -> 0.72;
            case MID -> 1.18;
            case HERO -> 1.85;
        };
        return base * (0.82 + history.vigor() * 0.24);
    }

    private static int trunkParentFor(double vertical, int trunkSections) {
        int index = (int)Math.floor(vertical * trunkSections);
        return Math.max(0, Math.min(trunkSections - 1, index));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
