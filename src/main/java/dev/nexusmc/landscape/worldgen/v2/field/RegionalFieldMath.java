package dev.nexusmc.landscape.worldgen.v2.field;

import java.util.Locale;

/**
 * Allocation-free composition of the semantic V2 regional fields.
 *
 * <p>The density function and the diagnostic sampler both call this class,
 * so province maps cannot silently diverge from the uplift used by terrain.
 */
public final class RegionalFieldMath {
    public static final double WARP_SCALE = 1.0 / 1_536.0;
    public static final double WARP_BLOCKS = 420.0;
    public static final double MACRO_SCALE = 1.0 / 3_072.0;
    public static final double DETAIL_SCALE = 1.0 / 1_536.0;
    public static final double RIDGE_SCALE = 1.0 / 2_048.0;
    public static final double VOLCANIC_SCALE = 1.0 / 5_120.0;
    public static final double COMPOSITION_SCALE = 1.0 / 4_096.0;
    public static final double CLIMATE_SCALE = 1.0 / 2_560.0;

    private static final Province[] PROVINCES = Province.values();
    private static final Mood[] MOODS = Mood.values();

    private RegionalFieldMath() {
    }

    public static double compute(
        Channel channel,
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        return switch (channel.kind) {
            case PROVINCE -> provinceWeight(
                channel.index,
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic
            );
            case MOOD -> moodWeight(
                channel.index,
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic,
                composition
            );
            case RHYTHM -> rhythm(
                composition,
                detail,
                continentalness
            ).encodedValue();
            case HIERARCHY -> hierarchyStrength(
                continentalness,
                macro,
                detail,
                ridge,
                volcanic,
                composition
            );
            case UPLIFT -> macroUplift(
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic,
                composition
            );
            case DOMINANT_PROVINCE -> dominantProvince(
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic
            ).ordinal() / (double)(PROVINCES.length - 1);
            case DOMINANT_MOOD -> dominantMood(
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic,
                composition
            ).ordinal() / (double)(MOODS.length - 1);
        };
    }

    public static Sample sample(
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        double[] provinceWeights = new double[PROVINCES.length];
        for (int index = 0; index < provinceWeights.length; index++) {
            provinceWeights[index] = provinceWeight(
                index,
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic
            );
        }

        double[] moodWeights = new double[MOODS.length];
        for (int index = 0; index < moodWeights.length; index++) {
            moodWeights[index] = moodWeight(
                index,
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

        return new Sample(
            provinceWeights,
            moodWeights,
            rhythm(composition, detail, continentalness),
            hierarchyStrength(
                continentalness,
                macro,
                detail,
                ridge,
                volcanic,
                composition
            ),
            macroUplift(
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic,
                composition
            )
        );
    }

    private static double provinceWeight(
        int provinceIndex,
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic
    ) {
        double sum = 0.0;
        for (int index = 0; index < PROVINCES.length; index++) {
            sum += provinceScore(
                index,
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic
            );
        }
        return provinceScore(
            provinceIndex,
            continentalness,
            temperature,
            humidity,
            macro,
            detail,
            ridge,
            volcanic
        ) / Math.max(sum, 1.0E-9);
    }

    private static double provinceScore(
        int provinceIndex,
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic
    ) {
        double land = smoothstep(-0.46, -0.08, continentalness);
        double ocean = 1.0 - land;
        double rugged = clamp01(Math.abs(ridge) * 1.18);
        double lowRelief = 1.0 - rugged;
        double cold = smoothstep(0.05, 0.72, -temperature);
        double wet = smoothstep(-0.20, 0.68, humidity);
        double dry = smoothstep(-0.10, 0.72, -humidity);
        double positiveMacro = smoothstep(-0.25, 0.78, macro);
        double oldMacro = 1.0 - smoothstep(0.05, 0.82, macro);
        double rareVolcanic = smoothstep(0.48, 0.90, volcanic);
        double karstTexture = smoothstep(0.18, 0.82, detail);
        double mycelialSignal = smoothstep(0.68, 0.94, 0.58 * macro + 0.42 * detail);

        Province province = PROVINCES[provinceIndex];
        return 1.0E-4 + switch (province) {
            case SEDIMENTARY_LOWLAND ->
                land * (0.38 + 0.92 * lowRelief) * (0.62 + 0.38 * (1.0 - Math.abs(detail)));
            case WETLAND_BASIN ->
                land * wet * lowRelief * (0.48 + 0.72 * smoothstep(-0.85, -0.05, macro));
            case OLD_ERODED_HIGHLAND ->
                land * (0.30 + 0.82 * rugged) * (0.40 + 0.76 * oldMacro);
            case YOUNG_FOLD_MOUNTAINS ->
                land * rugged * positiveMacro * (0.55 + 0.45 * Math.abs(detail));
            case GLACIAL_MASSIF ->
                land * cold * (0.24 + 0.94 * rugged) * (0.38 + 0.62 * positiveMacro);
            case DRY_PLATEAU ->
                land * dry * (0.45 + 0.58 * Math.abs(macro)) * (0.54 + 0.46 * lowRelief);
            case VOLCANIC_BELT ->
                land * rareVolcanic * (0.52 + 0.68 * rugged) * (0.64 + 0.36 * positiveMacro);
            case KARST_BELT ->
                land * wet * karstTexture * (0.58 + 0.42 * lowRelief);
            case OCEANIC_CRUST ->
                ocean * (1.0 - 0.82 * mycelialSignal) * (0.82 + 0.18 * Math.abs(detail));
            case MYCELIAL_CRATON ->
                (0.82 * ocean + 0.18 * land) * mycelialSignal * (0.42 + 0.58 * lowRelief);
        };
    }

    private static double moodWeight(
        int moodIndex,
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        double sum = 0.0;
        for (int index = 0; index < MOODS.length; index++) {
            sum += moodScore(
                index,
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
        return moodScore(
            moodIndex,
            continentalness,
            temperature,
            humidity,
            macro,
            detail,
            ridge,
            volcanic,
            composition
        ) / Math.max(sum, 1.0E-9);
    }

    private static double moodScore(
        int moodIndex,
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        double land = smoothstep(-0.46, -0.08, continentalness);
        double ocean = 1.0 - land;
        double rugged = clamp01(Math.abs(ridge) * 1.18);
        double open = clamp01(0.52 - 0.30 * rugged - 0.18 * Math.abs(detail) + 0.26 * -composition);
        double wet = smoothstep(-0.20, 0.68, humidity);
        double dry = smoothstep(-0.10, 0.72, -humidity);
        double cold = smoothstep(0.05, 0.72, -temperature);
        double wonder = smoothstep(0.50, 0.92, Math.abs(composition));

        Mood mood = MOODS[moodIndex];
        return 1.0E-4 + switch (mood) {
            case TRANQUIL -> (0.45 + open) * (0.58 + 0.42 * wet) * (0.70 + 0.30 * (1.0 - wonder));
            case PASTORAL -> land * open * (0.52 + 0.48 * wet) * (0.66 + 0.34 * (1.0 - rugged));
            case MYSTERIOUS -> land * wet * (0.42 + 0.72 * (1.0 - open)) * (0.54 + 0.46 * Math.abs(detail));
            case ANCIENT -> land * (0.44 + 0.64 * rugged) * (0.52 + 0.48 * smoothstep(-0.8, 0.1, -macro));
            case MONUMENTAL -> land * rugged * (0.42 + 0.78 * smoothstep(0.16, 0.86, macro));
            case DESOLATE -> (0.30 + dry + 0.35 * cold) * (0.56 + 0.44 * open);
            case ENCHANTED -> land * wet * wonder * (0.54 + 0.46 * (1.0 - rugged));
            case DANGEROUS -> land * (0.35 + rugged) * (0.50 + 0.50 * smoothstep(0.05, 0.86, composition + volcanic * 0.3));
            case SACRED -> land * wonder * (0.46 + 0.54 * open) * (0.64 + 0.36 * wet);
            case MELANCHOLIC -> (0.42 + ocean + 0.38 * cold) * (0.50 + 0.50 * open) * (0.72 + 0.28 * wet);
        };
    }

    private static double macroUplift(
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        double provinceSum = 0.0;
        double weightedRelief = 0.0;
        for (int index = 0; index < PROVINCES.length; index++) {
            double score = provinceScore(
                index,
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic
            );
            provinceSum += score;
            weightedRelief += score * PROVINCES[index].relief;
        }

        double rugged = clamp01(Math.abs(ridge) * 1.18);
        double ridgeShape = 0.22 + 0.78 * Math.pow(rugged, 1.35);
        Rhythm rhythm = rhythm(composition, detail, continentalness);
        double rhythmMultiplier = switch (rhythm) {
            case QUIET -> 0.22;
            case TRANSITIONAL -> 0.58;
            case DRAMATIC -> 1.0;
            case RECOVERY -> 0.36;
        };
        double relief = weightedRelief / Math.max(provinceSum, 1.0E-9);
        double asymmetricDetail = 0.82 + 0.18 * clamp(detail, -1.0, 1.0);
        return clamp(relief * ridgeShape * rhythmMultiplier * asymmetricDetail, -0.12, 1.0);
    }

    private static double hierarchyStrength(
        double continentalness,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        double land = smoothstep(-0.46, -0.08, continentalness);
        double focal = Math.max(
            Math.abs(composition),
            0.64 * smoothstep(0.42, 0.92, volcanic) + 0.36 * Math.abs(macro)
        );
        double silhouette = 0.55 * Math.abs(ridge) + 0.45 * Math.abs(detail);
        return clamp01(land * smoothstep(0.46, 0.90, focal) * (0.45 + 0.55 * silhouette));
    }

    private static Rhythm rhythm(double composition, double detail, double continentalness) {
        double phase = clamp(
            0.68 * composition + 0.22 * detail + 0.10 * continentalness,
            -1.0,
            1.0
        );
        if (phase < -0.36) {
            return Rhythm.QUIET;
        }
        if (phase < 0.04) {
            return Rhythm.TRANSITIONAL;
        }
        if (phase > 0.46) {
            return Rhythm.DRAMATIC;
        }
        return Rhythm.RECOVERY;
    }

    private static Province dominantProvince(
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic
    ) {
        int bestIndex = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < PROVINCES.length; index++) {
            double score = provinceScore(
                index,
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic
            );
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return PROVINCES[bestIndex];
    }

    private static Mood dominantMood(
        double continentalness,
        double temperature,
        double humidity,
        double macro,
        double detail,
        double ridge,
        double volcanic,
        double composition
    ) {
        int bestIndex = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < MOODS.length; index++) {
            double score = moodScore(
                index,
                continentalness,
                temperature,
                humidity,
                macro,
                detail,
                ridge,
                volcanic,
                composition
            );
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return MOODS[bestIndex];
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Province {
        SEDIMENTARY_LOWLAND(0.16),
        WETLAND_BASIN(-0.08),
        OLD_ERODED_HIGHLAND(0.46),
        YOUNG_FOLD_MOUNTAINS(0.96),
        GLACIAL_MASSIF(0.82),
        DRY_PLATEAU(0.42),
        VOLCANIC_BELT(0.90),
        KARST_BELT(0.30),
        OCEANIC_CRUST(-0.10),
        MYCELIAL_CRATON(0.22);

        private final double relief;

        Province(double relief) {
            this.relief = relief;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Mood {
        TRANQUIL,
        PASTORAL,
        MYSTERIOUS,
        ANCIENT,
        MONUMENTAL,
        DESOLATE,
        ENCHANTED,
        DANGEROUS,
        SACRED,
        MELANCHOLIC;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Rhythm {
        QUIET(0.0),
        TRANSITIONAL(1.0 / 3.0),
        RECOVERY(2.0 / 3.0),
        DRAMATIC(1.0);

        private final double encodedValue;

        Rhythm(double encodedValue) {
            this.encodedValue = encodedValue;
        }

        public double encodedValue() {
            return encodedValue;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Channel {
        PROVINCE_SEDIMENTARY_LOWLAND(Kind.PROVINCE, Province.SEDIMENTARY_LOWLAND.ordinal()),
        PROVINCE_WETLAND_BASIN(Kind.PROVINCE, Province.WETLAND_BASIN.ordinal()),
        PROVINCE_OLD_ERODED_HIGHLAND(Kind.PROVINCE, Province.OLD_ERODED_HIGHLAND.ordinal()),
        PROVINCE_YOUNG_FOLD_MOUNTAINS(Kind.PROVINCE, Province.YOUNG_FOLD_MOUNTAINS.ordinal()),
        PROVINCE_GLACIAL_MASSIF(Kind.PROVINCE, Province.GLACIAL_MASSIF.ordinal()),
        PROVINCE_DRY_PLATEAU(Kind.PROVINCE, Province.DRY_PLATEAU.ordinal()),
        PROVINCE_VOLCANIC_BELT(Kind.PROVINCE, Province.VOLCANIC_BELT.ordinal()),
        PROVINCE_KARST_BELT(Kind.PROVINCE, Province.KARST_BELT.ordinal()),
        PROVINCE_OCEANIC_CRUST(Kind.PROVINCE, Province.OCEANIC_CRUST.ordinal()),
        PROVINCE_MYCELIAL_CRATON(Kind.PROVINCE, Province.MYCELIAL_CRATON.ordinal()),
        MOOD_TRANQUIL(Kind.MOOD, Mood.TRANQUIL.ordinal()),
        MOOD_PASTORAL(Kind.MOOD, Mood.PASTORAL.ordinal()),
        MOOD_MYSTERIOUS(Kind.MOOD, Mood.MYSTERIOUS.ordinal()),
        MOOD_ANCIENT(Kind.MOOD, Mood.ANCIENT.ordinal()),
        MOOD_MONUMENTAL(Kind.MOOD, Mood.MONUMENTAL.ordinal()),
        MOOD_DESOLATE(Kind.MOOD, Mood.DESOLATE.ordinal()),
        MOOD_ENCHANTED(Kind.MOOD, Mood.ENCHANTED.ordinal()),
        MOOD_DANGEROUS(Kind.MOOD, Mood.DANGEROUS.ordinal()),
        MOOD_SACRED(Kind.MOOD, Mood.SACRED.ordinal()),
        MOOD_MELANCHOLIC(Kind.MOOD, Mood.MELANCHOLIC.ordinal()),
        RHYTHM(Kind.RHYTHM, -1),
        HIERARCHY(Kind.HIERARCHY, -1),
        MACRO_UPLIFT(Kind.UPLIFT, -1),
        DOMINANT_PROVINCE(Kind.DOMINANT_PROVINCE, -1),
        DOMINANT_MOOD(Kind.DOMINANT_MOOD, -1);

        private final Kind kind;
        private final int index;

        Channel(Kind kind, int index) {
            this.kind = kind;
            this.index = index;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Channel fromSerializedName(String name) {
            for (Channel channel : values()) {
                if (channel.serializedName().equals(name)) {
                    return channel;
                }
            }
            throw new IllegalArgumentException("Unknown Nexus regional field channel: " + name);
        }
    }

    private enum Kind {
        PROVINCE,
        MOOD,
        RHYTHM,
        HIERARCHY,
        UPLIFT,
        DOMINANT_PROVINCE,
        DOMINANT_MOOD
    }

    public record Sample(
        double[] provinceWeights,
        double[] moodWeights,
        Rhythm rhythm,
        double hierarchyStrength,
        double macroUplift
    ) {
        public Sample {
            provinceWeights = provinceWeights.clone();
            moodWeights = moodWeights.clone();
        }

        @Override
        public double[] provinceWeights() {
            return provinceWeights.clone();
        }

        @Override
        public double[] moodWeights() {
            return moodWeights.clone();
        }

        public double provinceWeight(Province province) {
            return provinceWeights[province.ordinal()];
        }

        public double moodWeight(Mood mood) {
            return moodWeights[mood.ordinal()];
        }

        public Province dominantProvince() {
            return PROVINCES[indexOfMaximum(provinceWeights)];
        }

        public Mood dominantMood() {
            return MOODS[indexOfMaximum(moodWeights)];
        }

        private static int indexOfMaximum(double[] values) {
            int best = 0;
            for (int index = 1; index < values.length; index++) {
                if (values[index] > values[best]) {
                    best = index;
                }
            }
            return best;
        }
    }
}
