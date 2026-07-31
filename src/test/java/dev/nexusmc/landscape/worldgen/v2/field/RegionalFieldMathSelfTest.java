package dev.nexusmc.landscape.worldgen.v2.field;

import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.util.BoundedConcurrentCache;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Framework-free verification entry point, invoked by Gradle's
 * regionalFieldTest task.
 */
public final class RegionalFieldMathSelfTest {
    private RegionalFieldMathSelfTest() {
    }

    public static void main(String[] arguments) {
        verifyNormalizationAndRanges();
        verifyDeterminism();
        verifyContinuousChunkBoundary();
        verifySyntheticCoverageAndRhythmBudget();
        verifyLandformChannels();
        verifyHydrologyGraph();
        verifyActiveRiverNetworkQuality();
        verifyHydrologyRequestOrderAndThreads();
        verifyBasinSeamsAndConcurrency();
        verifyBoundedConcurrentCache();
        System.out.println("RegionalFieldMathSelfTest: PASS");
    }

    private static void verifyBoundedConcurrentCache() {
        BoundedConcurrentCache<Integer, Integer> cache =
            new BoundedConcurrentCache<>(64);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                int offset = worker * 1_000;
                futures.add(executor.submit(() -> {
                    for (int index = 0; index < 1_000; index++) {
                        cache.put(offset + index, index);
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception exception) {
                    throw new AssertionError("bounded cache worker failed", exception);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        require(cache.size() <= 64, "bounded cache exceeded capacity");
        cache.clear();
        require(cache.size() == 0, "bounded cache clear failed");
    }

    private static void verifyNormalizationAndRanges() {
        RegionalFieldMath.Sample sample = RegionalFieldMath.sample(
            0.24,
            -0.38,
            0.51,
            0.62,
            0.44,
            -0.73,
            0.81,
            0.56
        );
        requireClose(sum(sample.provinceWeights()), 1.0, 1.0E-9, "province normalization");
        requireClose(sum(sample.moodWeights()), 1.0, 1.0E-9, "mood normalization");
        requireRange(sample.hierarchyStrength(), 0.0, 1.0, "hierarchy");
        requireRange(sample.macroUplift(), -0.12, 1.0, "macro uplift");
        for (double weight : sample.provinceWeights()) {
            requireRange(weight, 0.0, 1.0, "province weight");
        }
        for (double weight : sample.moodWeights()) {
            requireRange(weight, 0.0, 1.0, "mood weight");
        }
    }

    private static void verifyDeterminism() {
        RegionalFieldMath.Sample first = syntheticSample(12_345, -9_876);
        RegionalFieldMath.Sample second = syntheticSample(12_345, -9_876);
        require(
            first.dominantProvince() == second.dominantProvince(),
            "dominant province changed for identical coordinates"
        );
        require(
            first.dominantMood() == second.dominantMood(),
            "dominant mood changed for identical coordinates"
        );
        require(first.rhythm() == second.rhythm(), "rhythm changed for identical coordinates");
        requireClose(
            first.macroUplift(),
            second.macroUplift(),
            0.0,
            "uplift determinism"
        );
    }

    private static void verifyContinuousChunkBoundary() {
        for (int z = -256; z <= 256; z += 8) {
            RegionalFieldMath.Sample left = syntheticSample(15, z);
            RegionalFieldMath.Sample right = syntheticSample(16, z);
            require(
                Math.abs(left.macroUplift() - right.macroUplift()) < 0.04,
                "uplift discontinuity at chunk boundary z=" + z
            );
            require(
                Math.abs(left.hierarchyStrength() - right.hierarchyStrength()) < 0.04,
                "hierarchy discontinuity at chunk boundary z=" + z
            );
        }
    }

    private static void verifySyntheticCoverageAndRhythmBudget() {
        Set<RegionalFieldMath.Province> provinces =
            EnumSet.noneOf(RegionalFieldMath.Province.class);
        Set<RegionalFieldMath.Mood> moods =
            EnumSet.noneOf(RegionalFieldMath.Mood.class);
        int dramatic = 0;
        int samples = 0;

        for (int z = -32_768; z <= 32_768; z += 256) {
            for (int x = -32_768; x <= 32_768; x += 256) {
                RegionalFieldMath.Sample sample = syntheticSample(x, z);
                provinces.add(sample.dominantProvince());
                moods.add(sample.dominantMood());
                if (sample.rhythm() == RegionalFieldMath.Rhythm.DRAMATIC) {
                    dramatic++;
                }
                samples++;
            }
        }

        require(provinces.size() >= 6, "fewer than six synthetic provinces: " + provinces);
        require(moods.size() >= 6, "fewer than six synthetic moods: " + moods);
        double dramaticShare = dramatic / (double)samples;
        require(
            dramaticShare <= 0.35,
            "dramatic share exceeds contract: " + dramaticShare
        );
        System.out.printf(
            "coverage provinces=%d moods=%d dramatic=%.4f%n",
            provinces.size(),
            moods.size(),
            dramaticShare
        );
    }

    private static void verifyLandformChannels() {
        int activeFamilies = 0;
        RegionalFieldMath.Channel[] channels = {
            RegionalFieldMath.Channel.YOUNG_MOUNTAINS,
            RegionalFieldMath.Channel.OLD_MOUNTAINS,
            RegionalFieldMath.Channel.PLATEAU,
            RegionalFieldMath.Channel.VOLCANIC_MOUNTAINS,
            RegionalFieldMath.Channel.GLACIER_MASS,
            RegionalFieldMath.Channel.CANYON_INCISION,
            RegionalFieldMath.Channel.COAST_WEIGHT,
            RegionalFieldMath.Channel.LANDFORM_OFFSET
        };
        double[] maxima = new double[channels.length];
        for (int z = -32_768; z <= 32_768; z += 256) {
            for (int x = -32_768; x <= 32_768; x += 256) {
                double[] inputs = syntheticInputs(x, z);
                for (int index = 0; index < channels.length; index++) {
                    double value = RegionalFieldMath.compute(
                        channels[index],
                        inputs[0],
                        inputs[1],
                        inputs[2],
                        inputs[3],
                        inputs[4],
                        inputs[5],
                        inputs[6],
                        inputs[7]
                    );
                    requireRange(value, 0.0, 1.0, channels[index].serializedName());
                    maxima[index] = Math.max(maxima[index], value);
                }
            }
        }
        for (int index = 0; index < 6; index++) {
            if (maxima[index] > 0.08) {
                activeFamilies++;
            }
        }
        require(activeFamilies >= 4, "too few active landform families: " + activeFamilies);
        require(maxima[6] > 0.5, "coast field never becomes active");
        require(maxima[7] > 0.1, "landform offset never becomes active");
        System.out.printf("landforms active=%d offsetMax=%.4f%n", activeFamilies, maxima[7]);
    }

    private static void verifyHydrologyGraph() {
        HydrologyMath.NoiseSource noise = syntheticHydrologyNoise();
        int seamChecks = 0;
        for (int z = -12_000; z <= 12_000; z += 257) {
            for (int boundary = -512; boundary <= 512; boundary += 16) {
                int x = boundary * 16;
                HydrologyMath.Sample left = HydrologyMath.sample(x - 1, z, noise);
                HydrologyMath.Sample right = HydrologyMath.sample(x, z, noise);
                require(
                    Math.abs(left.distance() - right.distance()) <= 1.05,
                    "river distance discontinuity at chunk boundary"
                );
                require(
                    Math.abs(left.mask() - right.mask()) < 0.08,
                    "river mask discontinuity at chunk boundary"
                );
                require(
                    Math.abs(left.signedDistance() - right.signedDistance()) <= 1.05,
                    "signed river distance discontinuity at chunk boundary"
                );
                if (left.mask() > 0.2 && right.mask() > 0.2) {
                    require(
                        Math.abs(left.bedY() - right.bedY()) < 3.0,
                        "river bed discontinuity at chunk boundary"
                    );
                    require(
                        Math.abs(left.waterY() - right.waterY()) < 3.0,
                        "river water discontinuity at chunk boundary"
                    );
                }
                seamChecks++;
            }
        }

        int downhillChecks = 0;
        for (int cellZ = -32; cellZ <= 32; cellZ++) {
            for (int cellX = -32; cellX <= 32; cellX++) {
                HydrologyMath.Node source = HydrologyMath.node(cellX, cellZ, noise);
                HydrologyMath.Node target = HydrologyMath.downstream(source, noise);
                if (target != null) {
                    require(
                        target.bedY() < source.bedY(),
                        "drainage edge flows uphill"
                    );
                    downhillChecks++;
                }
            }
        }
        require(downhillChecks >= 20, "insufficient downhill river checks: " + downhillChecks);

        int convergences = 0;
        int terminalNodes = 0;
        int lakeNodes = 0;
        int openLakeNodes = 0;
        int overflowNodes = 0;
        for (int cellZ = -12; cellZ <= 12; cellZ++) {
            for (int cellX = -12; cellX <= 12; cellX++) {
                int incoming = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        HydrologyMath.Node neighbour =
                            HydrologyMath.node(cellX + dx, cellZ + dz, noise);
                        HydrologyMath.Node target = HydrologyMath.downstream(neighbour, noise);
                        if (target != null
                            && target.cellX() == cellX
                            && target.cellZ() == cellZ) {
                            incoming++;
                        }
                    }
                }
                if (incoming >= 2) {
                    convergences++;
                }
                HydrologyMath.Node node = HydrologyMath.node(cellX, cellZ, noise);
                if (HydrologyMath.downstream(node, noise) != null) {
                    continue;
                }
                HydrologyMath.NodeInfo info = HydrologyMath.nodeInfo(node, noise);
                require(
                    info.canonicalNodeId() == HydrologyMath.canonicalNodeId(cellX, cellZ),
                    "canonical node id mismatch"
                );
                if (info.downstreamNodeId() == HydrologyMath.NO_NODE) {
                    terminalNodes++;
                    require(
                        info.terminalReason() != HydrologyMath.TerminalReason.NONE,
                        "terminal node has no reason"
                    );
                    if (info.terminalReason() == HydrologyMath.TerminalReason.LAKE) {
                        lakeNodes++;
                        HydrologyMath.LakeProfile lake =
                            HydrologyMath.lakeProfile(node, noise);
                        require(lake != null, "lake terminal has no bounded profile");
                        require(lake.maximumDepth() <= 12.0, "lake depth is unbounded");
                        require(lake.maximumArea() > 0.0, "lake area is empty");
                        HydrologyMath.BasinSample basin = HydrologyMath.basinSample(
                            (int)Math.round(node.x()),
                            (int)Math.round(node.z()),
                            noise
                        );
                        require(basin.mask() > 0.9, "lake center has no physical basin");
                        if (!lake.closedBasin()) {
                            require(
                                lake.outletId() != HydrologyMath.NO_NODE,
                                "open lake has no outlet"
                            );
                            openLakeNodes++;
                        }
                    }
                } else {
                    require(
                        info.terminalReason() == HydrologyMath.TerminalReason.NONE,
                        "non-terminal node has terminal reason"
                    );
                }
            }
        }
        for (int cellZ = -64; cellZ <= 64; cellZ++) {
            for (int cellX = -64; cellX <= 64; cellX++) {
                HydrologyMath.Node node = HydrologyMath.node(cellX, cellZ, noise);
                HydrologyMath.NodeInfo info = HydrologyMath.nodeInfo(node, noise);
                if (info.terminalReason()
                    == HydrologyMath.TerminalReason.DETERMINISTIC_OVERFLOW_OUTLET) {
                    HydrologyMath.Node outlet =
                        HydrologyMath.overflowOutlet(node, noise);
                    require(outlet != null, "overflow terminal has no breach outlet");
                    require(
                        info.outletId() == outlet.id(),
                        "overflow outlet metadata mismatch"
                    );
                    HydrologyMath.BasinSample spill = HydrologyMath.basinSample(
                        (int)Math.round(node.x()),
                        (int)Math.round(node.z()),
                        noise
                    );
                    require(
                        spill.reason()
                            == HydrologyMath.TerminalReason.DETERMINISTIC_OVERFLOW_OUTLET,
                        "overflow has no physical channel profile"
                    );
                    overflowNodes++;
                }
            }
        }
        require(convergences >= 10, "drainage graph has too few convergences: " + convergences);
        require(terminalNodes > 0, "drainage graph has no bounded terminals");
        require(lakeNodes > 0, "drainage graph has no bounded lake profile");
        System.out.printf(
            "hydrology seams=%d downhillChecks=%d convergences=%d terminals=%d lakes=%d%n",
            seamChecks,
            downhillChecks,
            convergences,
            terminalNodes,
            lakeNodes
        );
        System.out.printf(
            "hydrology physicalBasins openLakes=%d overflowChannels=%d%n",
            openLakeNodes,
            overflowNodes
        );
    }

    private static RegionalFieldMath.Sample syntheticSample(int x, int z) {
        double[] inputs = syntheticInputs(x, z);
        return RegionalFieldMath.sample(
            inputs[0],
            inputs[1],
            inputs[2],
            inputs[3],
            inputs[4],
            inputs[5],
            inputs[6],
            inputs[7]
        );
    }

    private static double[] syntheticInputs(int x, int z) {
        return new double[] {
            wave(x, z, 13_000.0, 0.17),
            wave(x + 31_337, z - 7_919, 9_000.0, 1.31),
            wave(x - 17_171, z + 4_099, 7_500.0, 2.17),
            wave(x + 2_003, z + 11_111, 5_800.0, 0.73),
            wave(x - 4_001, z - 5_003, 2_900.0, 1.91),
            wave(x + 9_001, z - 3_001, 4_200.0, 2.77),
            wave(x - 12_007, z + 19_009, 11_000.0, 0.41),
            wave(x + 23_021, z - 29_023, 8_200.0, 2.43)
        };
    }

    private static HydrologyMath.NoiseSource syntheticHydrologyNoise() {
        return new HydrologyMath.NoiseSource() {
            @Override
            public double layout(double x, double z) {
                return Math.sin(x * 1.37 + z * 0.71) * 0.73;
            }

            @Override
            public double tributary(double x, double z) {
                return Math.cos(x * 0.61 - z * 1.13) * 0.68;
            }

            @Override
            public double elevation(double x, double z) {
                return Math.sin(x * 0.43 + z * 0.29) * 0.62
                    + Math.cos(z * 0.17 - x * 0.11) * 0.24;
            }

            @Override
            public double terrainY(double x, double z) {
                return 130.0
                    + Math.sin(x / 8_000.0 + z / 13_000.0) * 34.0
                    + Math.cos(z / 5_500.0 - x / 9_000.0) * 18.0;
            }
        };
    }

    private static void verifyActiveRiverNetworkQuality() {
        HydrologyMath.NoiseSource noise = syntheticHydrologyNoise();
        Map<Long, List<List<HydrologyMath.CenterlinePoint>>> incoming =
            new HashMap<>();
        Map<Long, List<HydrologyMath.CenterlinePoint>> outgoing =
            new HashMap<>();
        Map<Long, HydrologyMath.Node> nodes = new HashMap<>();
        int routingSegments = 0;
        int activeSegments = 0;
        int downhillChecks = 0;
        int downhillAccepted = 0;
        double sinuositySum = 0.0;

        for (int cellZ = -32; cellZ <= 32; cellZ++) {
            for (int cellX = -32; cellX <= 32; cellX++) {
                HydrologyMath.Node source =
                    HydrologyMath.node(cellX, cellZ, noise);
                HydrologyMath.Node target =
                    HydrologyMath.downstream(source, noise);
                nodes.put(source.id(), source);
                if (target == null) {
                    continue;
                }
                routingSegments++;
                if (!HydrologyMath.isChannelSegment(source, target, noise)) {
                    continue;
                }
                activeSegments++;
                nodes.put(target.id(), target);
                List<HydrologyMath.CenterlinePoint> points =
                    HydrologyMath.segmentPoints(source, target, noise, 24);
                outgoing.put(source.id(), points);
                incoming.computeIfAbsent(
                    target.id(),
                    ignored -> new ArrayList<>()
                ).add(points);
                double chord = Math.hypot(
                    target.x() - source.x(),
                    target.z() - source.z()
                );
                double length = 0.0;
                for (int index = 1; index < points.size(); index++) {
                    HydrologyMath.CenterlinePoint previous =
                        points.get(index - 1);
                    HydrologyMath.CenterlinePoint point = points.get(index);
                    length += Math.hypot(
                        point.x() - previous.x(),
                        point.z() - previous.z()
                    );
                    downhillChecks++;
                    if (point.bedY() < previous.bedY()) {
                        downhillAccepted++;
                    }
                }
                sinuositySum += length / Math.max(1.0, chord);
            }
        }

        int maximumTerminalInputs = 0;
        List<Double> junctionAngles = new ArrayList<>();
        for (Map.Entry<Long, List<List<HydrologyMath.CenterlinePoint>>> entry
            : incoming.entrySet()) {
            HydrologyMath.Node junction = nodes.get(entry.getKey());
            if (junction == null) {
                continue;
            }
            List<HydrologyMath.CenterlinePoint> outgoingPoints =
                outgoing.get(junction.id());
            if (outgoingPoints == null) {
                maximumTerminalInputs = Math.max(
                    maximumTerminalInputs,
                    entry.getValue().size()
                );
                continue;
            }
            HydrologyMath.CenterlinePoint next = outgoingPoints.get(1);
            for (List<HydrologyMath.CenterlinePoint> incomingPoints
                : entry.getValue()) {
                HydrologyMath.CenterlinePoint previous =
                    incomingPoints.get(incomingPoints.size() - 2);
                HydrologyMath.CenterlinePoint point =
                    incomingPoints.get(incomingPoints.size() - 1);
                junctionAngles.add(angleDegrees(
                    point.x() - previous.x(),
                    point.z() - previous.z(),
                    next.x() - point.x(),
                    next.z() - point.z()
                ));
            }
        }
        Collections.sort(junctionAngles);
        double activeShare = activeSegments / (double)routingSegments;
        double meanSinuosity = sinuositySum / activeSegments;
        double p90Angle = junctionAngles.get(
            (int)Math.floor((junctionAngles.size() - 1) * 0.90)
        );
        require(activeSegments > 300, "active river graph is too sparse");
        require(
            activeShare >= 0.35 && activeShare <= 0.72,
            "active river share is outside quality budget: " + activeShare
        );
        require(
            maximumTerminalInputs <= 3,
            "terminal river star exceeds three inputs: " + maximumTerminalInputs
        );
        require(
            meanSinuosity >= 1.025 && meanSinuosity <= 1.20,
            "river sinuosity outside quality budget: " + meanSinuosity
        );
        require(
            p90Angle <= 35.0,
            "final centerline P90 junction angle is too sharp: " + p90Angle
        );
        require(
            downhillAccepted == downhillChecks,
            "centerline bed profile is not strictly downhill"
        );
        System.out.printf(
            "active river network routing=%d active=%d share=%.3f "
                + "terminalMax=%d sinuosity=%.4f junctionP90=%.2f downhill=PASS%n",
            routingSegments,
            activeSegments,
            activeShare,
            maximumTerminalInputs,
            meanSinuosity,
            p90Angle
        );
    }

    private static double angleDegrees(
        double firstX,
        double firstZ,
        double secondX,
        double secondZ
    ) {
        double firstLength = Math.max(1.0E-9, Math.hypot(firstX, firstZ));
        double secondLength = Math.max(1.0E-9, Math.hypot(secondX, secondZ));
        double cosine = Math.max(
            -1.0,
            Math.min(
                1.0,
                (firstX * secondX + firstZ * secondZ)
                    / (firstLength * secondLength)
            )
        );
        return Math.toDegrees(Math.acos(cosine));
    }

    private static void verifyHydrologyRequestOrderAndThreads() {
        HydrologyMath.NoiseSource noise = syntheticHydrologyNoise();
        List<int[]> coordinates = new ArrayList<>();
        for (int z = -2_048; z <= 2_048; z += 137) {
            for (int x = -2_048; x <= 2_048; x += 193) {
                coordinates.add(new int[] {x, z});
            }
        }
        coordinates.add(new int[] {-1, -1});
        coordinates.add(new int[] {0, 0});
        coordinates.add(new int[] {15, 15});
        coordinates.add(new int[] {16, 16});

        Map<Long, HydrologyMath.Sample> baseline = new HashMap<>();
        for (int[] coordinate : coordinates) {
            baseline.put(
                coordinateKey(coordinate[0], coordinate[1]),
                HydrologyMath.sample(coordinate[0], coordinate[1], noise)
            );
        }
        List<int[]> reversed = new ArrayList<>(coordinates);
        Collections.reverse(reversed);
        for (int[] coordinate : reversed) {
            requireHydrologySampleEquals(
                baseline.get(coordinateKey(coordinate[0], coordinate[1])),
                HydrologyMath.sample(coordinate[0], coordinate[1], noise),
                "reverse request order"
            );
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<HydrologyMath.Sample>> futures = new ArrayList<>();
            for (int[] coordinate : coordinates) {
                futures.add(executor.submit(() ->
                    HydrologyMath.sample(coordinate[0], coordinate[1], noise)
                ));
            }
            for (int index = 0; index < coordinates.size(); index++) {
                int[] coordinate = coordinates.get(index);
                try {
                    requireHydrologySampleEquals(
                        baseline.get(coordinateKey(coordinate[0], coordinate[1])),
                        futures.get(index).get(),
                        "multi-thread request order"
                    );
                } catch (Exception exception) {
                    throw new AssertionError("parallel hydrology sampling failed", exception);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        System.out.printf(
            "hydrology request-order/thread samples=%d%n",
            coordinates.size()
        );
    }

    private static void verifyBasinSeamsAndConcurrency() {
        HydrologyMath.NoiseSource warmNoise = syntheticHydrologyNoise();
        List<int[]> coordinates = new ArrayList<>();
        for (int boundary = -64; boundary <= 64; boundary++) {
            int chunkBoundary = boundary * 16;
            coordinates.add(new int[] {chunkBoundary - 1, -769});
            coordinates.add(new int[] {chunkBoundary, -769});
            coordinates.add(new int[] {chunkBoundary - 1, 0});
            coordinates.add(new int[] {chunkBoundary, 0});
        }
        for (int tile = -8; tile <= 8; tile++) {
            int tileBoundary = tile * HydrologyMath.BASIN_SIZE;
            coordinates.add(new int[] {tileBoundary - 1, -17});
            coordinates.add(new int[] {tileBoundary, -17});
            coordinates.add(new int[] {-17, tileBoundary - 1});
            coordinates.add(new int[] {-17, tileBoundary});
        }
        for (int z : new int[] {-17, -16, -1, 0, 15, 16}) {
            for (int x : new int[] {-17, -16, -1, 0, 15, 16}) {
                coordinates.add(new int[] {x, z});
            }
        }
        int targetedBasins = 0;
        for (int cellZ = -32; cellZ <= 32 && targetedBasins < 6; cellZ++) {
            for (int cellX = -32; cellX <= 32 && targetedBasins < 6; cellX++) {
                HydrologyMath.Node node = HydrologyMath.node(
                    cellX,
                    cellZ,
                    warmNoise
                );
                if (HydrologyMath.downstream(node, warmNoise) != null) {
                    continue;
                }
                HydrologyMath.NodeInfo info = HydrologyMath.nodeInfo(
                    node,
                    warmNoise
                );
                if (info.terminalReason() == HydrologyMath.TerminalReason.OCEAN_OUTLET
                    || info.terminalReason() == HydrologyMath.TerminalReason.WETLAND_SINK) {
                    continue;
                }
                int centerX = (int)Math.round(node.x());
                int centerZ = (int)Math.round(node.z());
                int chunkBoundaryX = Math.floorDiv(centerX, 16) * 16;
                int chunkBoundaryZ = Math.floorDiv(centerZ, 16) * 16;
                coordinates.add(new int[] {centerX, centerZ});
                coordinates.add(new int[] {centerX + 1, centerZ});
                coordinates.add(new int[] {chunkBoundaryX - 1, centerZ});
                coordinates.add(new int[] {chunkBoundaryX, centerZ});
                coordinates.add(new int[] {
                    chunkBoundaryX - 1,
                    chunkBoundaryZ - 1
                });
                coordinates.add(new int[] {
                    chunkBoundaryX,
                    chunkBoundaryZ
                });
                targetedBasins++;
            }
        }

        Map<Long, HydrologyMath.BasinSample> baseline = new HashMap<>();
        for (int[] coordinate : coordinates) {
            baseline.put(
                coordinateKey(coordinate[0], coordinate[1]),
                HydrologyMath.basinSample(
                    coordinate[0],
                    coordinate[1],
                    warmNoise
                )
            );
        }
        List<int[]> reversed = new ArrayList<>(coordinates);
        Collections.reverse(reversed);
        HydrologyMath.NoiseSource coldNoise = syntheticHydrologyNoise();
        for (int[] coordinate : reversed) {
            HydrologyMath.BasinSample expected = baseline.get(
                coordinateKey(coordinate[0], coordinate[1])
            );
            requireBasinSampleEquals(
                expected,
                HydrologyMath.basinSample(
                    coordinate[0],
                    coordinate[1],
                    warmNoise
                ),
                "warm/reverse basin query"
            );
            requireBasinSampleEquals(
                expected,
                HydrologyMath.basinSample(
                    coordinate[0],
                    coordinate[1],
                    coldNoise
                ),
                "cold basin query"
            );
        }

        int continuityChecks = 0;
        for (int index = 1; index < coordinates.size(); index += 2) {
            int[] leftCoordinate = coordinates.get(index - 1);
            int[] rightCoordinate = coordinates.get(index);
            HydrologyMath.BasinSample left = HydrologyMath.basinSample(
                leftCoordinate[0],
                leftCoordinate[1],
                warmNoise
            );
            HydrologyMath.BasinSample right = HydrologyMath.basinSample(
                rightCoordinate[0],
                rightCoordinate[1],
                warmNoise
            );
            if (left.basinId() == HydrologyMath.NO_NODE
                || left.basinId() != right.basinId()
                || left.reason() != right.reason()) {
                continue;
            }
            require(
                Math.abs(left.mask() - right.mask()) < 0.20,
                "basin mask seam discontinuity"
            );
            require(
                Math.abs(left.shorelineWeight() - right.shorelineWeight()) < 0.08,
                "basin shoreline seam discontinuity"
            );
            require(
                Math.abs(left.bedY() - right.bedY()) < 1.25,
                "basin bed seam discontinuity"
            );
            require(
                Math.abs(left.waterY() - right.waterY()) < 1.25,
                "basin water seam discontinuity"
            );
            require(
                Math.abs(left.radialDistance() - right.radialDistance()) < 0.16,
                "basin radial seam discontinuity"
            );
            require(left.outletId() == right.outletId(), "basin outlet seam mismatch");
            continuityChecks++;
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<HydrologyMath.BasinSample>> futures = new ArrayList<>();
            for (int[] coordinate : coordinates) {
                futures.add(executor.submit(() -> HydrologyMath.basinSample(
                    coordinate[0],
                    coordinate[1],
                    warmNoise
                )));
            }
            for (int index = 0; index < coordinates.size(); index++) {
                int[] coordinate = coordinates.get(index);
                try {
                    requireBasinSampleEquals(
                        baseline.get(coordinateKey(coordinate[0], coordinate[1])),
                        futures.get(index).get(),
                        "multi-thread basin query"
                    );
                } catch (Exception exception) {
                    throw new AssertionError(
                        "parallel basin sampling failed",
                        exception
                    );
                }
            }
        } finally {
            executor.shutdownNow();
        }
        System.out.printf(
            "basin seams samples=%d targeted=%d continuity=%d cold/warm/reverse/thread=PASS%n",
            coordinates.size(),
            targetedBasins,
            continuityChecks
        );
    }

    private static long coordinateKey(int x, int z) {
        return ((long)x << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private static void requireHydrologySampleEquals(
        HydrologyMath.Sample expected,
        HydrologyMath.Sample actual,
        String description
    ) {
        requireClose(actual.mask(), expected.mask(), 0.0, description + " mask");
        requireClose(
            actual.signedDistance(),
            expected.signedDistance(),
            0.0,
            description + " signed distance"
        );
        requireClose(actual.bedY(), expected.bedY(), 0.0, description + " bed");
        requireClose(actual.waterY(), expected.waterY(), 0.0, description + " water");
        require(actual.order() == expected.order(), description + " order");
        require(
            actual.accumulation() == expected.accumulation(),
            description + " accumulation"
        );
        require(
            actual.canonicalSegmentId() == expected.canonicalSegmentId(),
            description + " canonical segment"
        );
    }

    private static void requireBasinSampleEquals(
        HydrologyMath.BasinSample expected,
        HydrologyMath.BasinSample actual,
        String description
    ) {
        require(expected.reason() == actual.reason(), description + " reason");
        require(expected.basinId() == actual.basinId(), description + " basin id");
        require(expected.outletId() == actual.outletId(), description + " outlet id");
        if (expected.reason() == HydrologyMath.TerminalReason.NONE) {
            return;
        }
        requireClose(actual.mask(), expected.mask(), 0.0, description + " mask");
        requireClose(
            actual.shorelineWeight(),
            expected.shorelineWeight(),
            0.0,
            description + " shoreline"
        );
        requireClose(actual.bedY(), expected.bedY(), 0.0, description + " bed");
        requireClose(actual.waterY(), expected.waterY(), 0.0, description + " water");
        requireClose(
            actual.radialDistance(),
            expected.radialDistance(),
            0.0,
            description + " radial"
        );
    }

    private static double wave(int x, int z, double scale, double phase) {
        return Math.sin(x / scale + phase)
            * 0.62
            + Math.cos(z / (scale * 0.83) - phase * 0.71)
            * 0.38;
    }

    private static double sum(double[] values) {
        double result = 0.0;
        for (double value : values) {
            result += value;
        }
        return result;
    }

    private static void requireRange(
        double value,
        double min,
        double max,
        String description
    ) {
        require(
            value >= min && value <= max,
            description + " outside [" + min + ", " + max + "]: " + value
        );
    }

    private static void requireClose(
        double actual,
        double expected,
        double tolerance,
        String description
    ) {
        require(
            Math.abs(actual - expected) <= tolerance,
            description + " expected=" + expected + " actual=" + actual
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
