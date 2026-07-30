package dev.nexusmc.landscape.worldgen.v2.field;

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

    private static RegionalFieldMath.Sample syntheticSample(int x, int z) {
        double continentalness = wave(x, z, 13_000.0, 0.17);
        double temperature = wave(x + 31_337, z - 7_919, 9_000.0, 1.31);
        double humidity = wave(x - 17_171, z + 4_099, 7_500.0, 2.17);
        double macro = wave(x + 2_003, z + 11_111, 5_800.0, 0.73);
        double detail = wave(x - 4_001, z - 5_003, 2_900.0, 1.91);
        double ridge = wave(x + 9_001, z - 3_001, 4_200.0, 2.77);
        double volcanic = wave(x - 12_007, z + 19_009, 11_000.0, 0.41);
        double composition = wave(x + 23_021, z - 29_023, 8_200.0, 2.43);
        return RegionalFieldMath.sample(
            continentalness,
            temperature,
            humidity,
            macro,
            detail,
            ridge,
            volcanic,
            composition
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
