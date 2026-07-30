package dev.nexusmc.landscape.worldgen.v2.field;

import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import java.util.EnumSet;
import java.util.Set;

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
        System.out.println("RegionalFieldMathSelfTest: PASS");
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
                        target.level() < source.level(),
                        "drainage edge flows uphill"
                    );
                    downhillChecks++;
                }
            }
        }
        require(downhillChecks >= 20, "insufficient downhill river checks: " + downhillChecks);

        int convergences = 0;
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
            }
        }
        require(convergences >= 10, "drainage graph has too few convergences: " + convergences);
        System.out.printf(
            "hydrology seams=%d downhillChecks=%d convergences=%d%n",
            seamChecks,
            downhillChecks,
            convergences
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
        };
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
