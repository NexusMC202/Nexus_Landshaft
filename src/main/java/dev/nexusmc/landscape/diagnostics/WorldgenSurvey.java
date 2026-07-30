package dev.nexusmc.landscape.diagnostics;

import com.mojang.logging.LogUtils;
import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Opt-in real-worldgen smoke test. It is inert in normal installations.
 * Set NEXUS_LANDSCAPE_SURVEY=1 for a server run to generate a height/biome
 * overview, statistics, and then stop the test server.
 */
public final class WorldgenSurvey {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_RADIUS_CHUNKS = 24;
    private static final int SAMPLE_STEP = 4;
    private static final int IMAGE_SCALE = 3;
    private static final int REGIONAL_ATLAS_SIZE = 384;
    private static final int REGIONAL_ATLAS_STEP = 128;
    private static final List<String> OVERWORLD_BIOMES_1_21_1 = List.of(
        "plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert",
        "swamp", "mangrove_swamp", "forest", "flower_forest", "birch_forest",
        "dark_forest", "old_growth_birch_forest", "old_growth_pine_taiga",
        "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna",
        "savanna_plateau", "windswept_hills", "windswept_gravelly_hills",
        "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle",
        "bamboo_jungle", "badlands", "eroded_badlands", "wooded_badlands",
        "meadow", "cherry_grove", "grove", "snowy_slopes", "frozen_peaks",
        "jagged_peaks", "stony_peaks", "river", "frozen_river", "beach",
        "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean",
        "deep_lukewarm_ocean", "ocean", "deep_ocean", "cold_ocean",
        "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean",
        "mushroom_fields", "dripstone_caves", "lush_caves", "deep_dark"
    );
    private static final Map<String, LongAdder> FEATURE_COUNTS = new ConcurrentHashMap<>();

    private WorldgenSurvey() {
    }

    public static void recordFeature(String name) {
        if ("1".equals(System.getenv("NEXUS_LANDSCAPE_SURVEY"))) {
            FEATURE_COUNTS.computeIfAbsent(name, ignored -> new LongAdder()).increment();
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        if (!"1".equals(System.getenv("NEXUS_LANDSCAPE_SURVEY"))) {
            return;
        }
        MinecraftServer server = event.getServer();
        server.execute(() -> runAndStop(server));
    }

    private static void runAndStop(MinecraftServer server) {
        try {
            FEATURE_COUNTS.clear();
            SurveyResult result = survey(server.overworld());
            Path output = Path.of("..", "build", "reports", "nexus-worldgen");
            Files.createDirectories(output);
            writeMap(result.colors(), output.resolve("survey.png"));
            writeMap(result.provinceColors(), output.resolve("province.png"));
            writeMap(result.moodColors(), output.resolve("mood.png"));
            writeMap(result.rhythmColors(), output.resolve("rhythm.png"));
            writeMap(result.hierarchyColors(), output.resolve("hierarchy.png"));
            writeMap(result.upliftColors(), output.resolve("macro-uplift.png"));
            Files.writeString(output.resolve("survey.txt"), result.report());
            RegionalAtlasResult atlas = surveyRegionalAtlas(server.overworld());
            writeMap(atlas.provinceColors(), output.resolve("province-atlas.png"));
            writeMap(atlas.moodColors(), output.resolve("mood-atlas.png"));
            writeMap(atlas.rhythmColors(), output.resolve("rhythm-atlas.png"));
            writeMap(atlas.hierarchyColors(), output.resolve("hierarchy-atlas.png"));
            writeMap(atlas.upliftColors(), output.resolve("macro-uplift-atlas.png"));
            Files.writeString(output.resolve("regional-atlas.txt"), atlas.report());
            if ("1".equals(System.getenv("NEXUS_LANDSCAPE_VERIFY_BIOMES"))) {
                Files.writeString(
                    output.resolve("biome-coverage.txt"),
                    verifyBiomeCoverage(server.overworld())
                );
            }
            LOGGER.info("Nexus worldgen survey complete: {}", output.toAbsolutePath().normalize());
        } catch (Exception exception) {
            LOGGER.error("Nexus worldgen survey failed", exception);
        } finally {
            server.halt(false);
        }
    }

    private static SurveyResult survey(ServerLevel level) {
        int radiusBlocks = surveyRadiusChunks() * 16;
        int size = radiusBlocks * 2 / SAMPLE_STEP;
        BlockPos center = resolveSurveyCenter(level);
        int centerX = center.getX();
        int centerZ = center.getZ();
        int[][] heights = new int[size][size];
        int[][] colors = new int[size][size];
        int[][] provinceColors = new int[size][size];
        int[][] moodColors = new int[size][size];
        int[][] rhythmColors = new int[size][size];
        int[][] hierarchyColors = new int[size][size];
        int[][] upliftColors = new int[size][size];
        Map<String, Integer> biomeCounts = new HashMap<>();
        Map<String, Integer> provinceCounts = new HashMap<>();
        Map<String, Integer> moodCounts = new HashMap<>();
        Map<String, Integer> rhythmCounts = new HashMap<>();
        NexusV2FieldSampler regionalSampler =
            new NexusV2FieldSampler(level.getChunkSource().randomState());
        long heightSum = 0;
        long heightSquareSum = 0;
        long slopeSum = 0;
        double hierarchySum = 0.0;
        double upliftSum = 0.0;
        int slopeSamples = 0;
        int waterSamples = 0;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        for (int imageZ = 0; imageZ < size; imageZ++) {
            int worldZ = centerZ - radiusBlocks + imageZ * SAMPLE_STEP;
            for (int imageX = 0; imageX < size; imageX++) {
                int worldX = centerX - radiusBlocks + imageX * SAMPLE_STEP;
                level.getChunk(worldX >> 4, worldZ >> 4);
                int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                BlockPos surface = new BlockPos(worldX, height - 1, worldZ);
                boolean water = level.getFluidState(surface).is(FluidTags.WATER);
                ResourceLocation biome = level.registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getKey(level.getBiome(surface).value());
                String biomeName = biome == null ? "minecraft:unknown" : biome.toString();
                RegionalFieldMath.Sample regional = regionalSampler.sample(worldX, worldZ);
                RegionalFieldMath.Province province = regional.dominantProvince();
                RegionalFieldMath.Mood mood = regional.dominantMood();
                RegionalFieldMath.Rhythm rhythm = regional.rhythm();

                heights[imageZ][imageX] = height;
                colors[imageZ][imageX] = biomeColor(biomeName, height, water);
                provinceColors[imageZ][imageX] = provinceColor(province);
                moodColors[imageZ][imageX] = moodColor(mood);
                rhythmColors[imageZ][imageX] = rhythmColor(rhythm);
                hierarchyColors[imageZ][imageX] = scalarColor(
                    regional.hierarchyStrength(),
                    new Color(15, 19, 28),
                    new Color(255, 210, 86)
                );
                upliftColors[imageZ][imageX] = scalarColor(
                    Math.max(0.0, regional.macroUplift()),
                    new Color(20, 38, 48),
                    new Color(238, 238, 225)
                );
                biomeCounts.merge(biomeName, 1, Integer::sum);
                provinceCounts.merge(province.serializedName(), 1, Integer::sum);
                moodCounts.merge(mood.serializedName(), 1, Integer::sum);
                rhythmCounts.merge(rhythm.serializedName(), 1, Integer::sum);
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
                heightSum += height;
                heightSquareSum += (long) height * height;
                hierarchySum += regional.hierarchyStrength();
                upliftSum += regional.macroUplift();
                if (water) {
                    waterSamples++;
                }
                if (imageX > 0) {
                    slopeSum += Math.abs(height - heights[imageZ][imageX - 1]);
                    slopeSamples++;
                }
                if (imageZ > 0) {
                    slopeSum += Math.abs(height - heights[imageZ - 1][imageX]);
                    slopeSamples++;
                }
            }
        }

        int sampleCount = size * size;
        double mean = heightSum / (double) sampleCount;
        double variance = heightSquareSum / (double) sampleCount - mean * mean;
        return new SurveyResult(
            colors,
            provinceColors,
            moodColors,
            rhythmColors,
            hierarchyColors,
            upliftColors,
            biomeCounts,
            provinceCounts,
            moodCounts,
            rhythmCounts,
            minHeight,
            maxHeight,
            mean,
            Math.sqrt(Math.max(0.0, variance)),
            slopeSum / (double) slopeSamples,
            waterSamples * 100.0 / sampleCount,
            hierarchySum / sampleCount,
            upliftSum / sampleCount
        );
    }

    private static RegionalAtlasResult surveyRegionalAtlas(ServerLevel level) {
        int[][] provinceColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] moodColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] rhythmColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] hierarchyColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] upliftColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        Map<String, Integer> provinceCounts = new HashMap<>();
        Map<String, Integer> moodCounts = new HashMap<>();
        Map<String, Integer> rhythmCounts = new HashMap<>();
        NexusV2FieldSampler sampler =
            new NexusV2FieldSampler(level.getChunkSource().randomState());
        int centerX = surveyCenter("NEXUS_LANDSCAPE_SURVEY_CENTER_X");
        int centerZ = surveyCenter("NEXUS_LANDSCAPE_SURVEY_CENTER_Z");
        int halfSpan = REGIONAL_ATLAS_SIZE * REGIONAL_ATLAS_STEP / 2;
        double hierarchySum = 0.0;
        double upliftSum = 0.0;
        int dramatic = 0;

        for (int imageZ = 0; imageZ < REGIONAL_ATLAS_SIZE; imageZ++) {
            int worldZ = centerZ - halfSpan + imageZ * REGIONAL_ATLAS_STEP;
            for (int imageX = 0; imageX < REGIONAL_ATLAS_SIZE; imageX++) {
                int worldX = centerX - halfSpan + imageX * REGIONAL_ATLAS_STEP;
                RegionalFieldMath.Sample sample = sampler.sample(worldX, worldZ);
                RegionalFieldMath.Province province = sample.dominantProvince();
                RegionalFieldMath.Mood mood = sample.dominantMood();
                RegionalFieldMath.Rhythm rhythm = sample.rhythm();
                provinceColors[imageZ][imageX] = provinceColor(province);
                moodColors[imageZ][imageX] = moodColor(mood);
                rhythmColors[imageZ][imageX] = rhythmColor(rhythm);
                hierarchyColors[imageZ][imageX] = scalarColor(
                    sample.hierarchyStrength(),
                    new Color(15, 19, 28),
                    new Color(255, 210, 86)
                );
                upliftColors[imageZ][imageX] = scalarColor(
                    Math.max(0.0, sample.macroUplift()),
                    new Color(20, 38, 48),
                    new Color(238, 238, 225)
                );
                provinceCounts.merge(province.serializedName(), 1, Integer::sum);
                moodCounts.merge(mood.serializedName(), 1, Integer::sum);
                rhythmCounts.merge(rhythm.serializedName(), 1, Integer::sum);
                hierarchySum += sample.hierarchyStrength();
                upliftSum += sample.macroUplift();
                if (rhythm == RegionalFieldMath.Rhythm.DRAMATIC) {
                    dramatic++;
                }
            }
        }

        int sampleCount = REGIONAL_ATLAS_SIZE * REGIONAL_ATLAS_SIZE;
        return new RegionalAtlasResult(
            provinceColors,
            moodColors,
            rhythmColors,
            hierarchyColors,
            upliftColors,
            provinceCounts,
            moodCounts,
            rhythmCounts,
            hierarchySum / sampleCount,
            upliftSum / sampleCount,
            dramatic * 100.0 / sampleCount,
            centerX,
            centerZ,
            halfSpan
        );
    }

    private static BlockPos resolveSurveyCenter(ServerLevel level) {
        int configuredX = surveyCenter("NEXUS_LANDSCAPE_SURVEY_CENTER_X");
        int configuredZ = surveyCenter("NEXUS_LANDSCAPE_SURVEY_CENTER_Z");
        String targetBiome = System.getenv("NEXUS_LANDSCAPE_SURVEY_TARGET_BIOME");
        if (targetBiome == null || targetBiome.isBlank()) {
            return new BlockPos(configuredX, 80, configuredZ);
        }

        var nearest = level.findClosestBiome3d(
            holder -> holder.unwrapKey()
                .map(key -> key.location().toString().equals(targetBiome))
                .orElse(false),
            new BlockPos(configuredX, 80, configuredZ),
            100_000,
            64,
            64
        );
        if (nearest == null) {
            LOGGER.warn(
                "Could not find target biome {} near {}, {}; using configured center",
                targetBiome,
                configuredX,
                configuredZ
            );
            return new BlockPos(configuredX, 80, configuredZ);
        }
        LOGGER.info(
            "Survey target biome {} found at {}",
            targetBiome,
            nearest.getFirst()
        );
        return nearest.getFirst();
    }

    private static String verifyBiomeCoverage(ServerLevel level) {
        StringBuilder report = new StringBuilder("Minecraft 1.21.1 Overworld biome source audit\n");
        Map<String, Integer> observed = new HashMap<>();
        int range = Math.max(
            8_192,
            Math.min(
                131_072,
                environmentInteger("NEXUS_LANDSCAPE_BIOME_AUDIT_RANGE", 65_536)
            )
        );
        int step = Math.max(
            64,
            Math.min(
                1_024,
                environmentInteger("NEXUS_LANDSCAPE_BIOME_AUDIT_STEP", 128)
            )
        );
        int[] sampleHeights = {-40, 8, 64, 96, 144};
        var biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();

        for (int z = -range; z <= range; z += step) {
            for (int x = -range; x <= range; x += step) {
                for (int y : sampleHeights) {
                    var holder = biomeSource.getNoiseBiome(
                        QuartPos.fromBlock(x),
                        QuartPos.fromBlock(y),
                        QuartPos.fromBlock(z),
                        sampler
                    );
                    holder.unwrapKey().ifPresent(key ->
                        observed.merge(key.location().toString(), 1, Integer::sum)
                    );
                }
            }
        }

        int found = 0;
        for (String path : OVERWORLD_BIOMES_1_21_1) {
            String biomeName = "minecraft:" + path;
            int count = observed.getOrDefault(biomeName, 0);
            report.append(count == 0 ? "MISSING " : "FOUND ")
                .append(biomeName)
                .append(" samples=")
                .append(count)
                .append('\n');
            if (count > 0) {
                found++;
            }
        }
        report.insert(
            report.indexOf("\n") + 1,
            String.format(
                Locale.ROOT,
                "found=%d expected=%d%n",
                found,
                OVERWORLD_BIOMES_1_21_1.size()
            )
        );
        return report.toString();
    }

    private static int surveyCenter(String axisName) {
        return environmentInteger(
            axisName,
            environmentInteger("NEXUS_LANDSCAPE_SURVEY_CENTER", 20_000)
        );
    }

    private static int surveyRadiusChunks() {
        return Math.max(
            4,
            Math.min(
                64,
                environmentInteger("NEXUS_LANDSCAPE_SURVEY_RADIUS_CHUNKS", DEFAULT_RADIUS_CHUNKS)
            )
        );
    }

    private static int environmentInteger(String name, int fallback) {
        String configured = System.getenv(name);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(configured);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Invalid {} '{}'; using {}", name, configured, fallback);
            return fallback;
        }
    }

    private static int biomeColor(String biome, int height, boolean water) {
        Color base;
        if (water || biome.contains("ocean")) {
            base = biome.contains("warm") ? new Color(35, 155, 183) : new Color(35, 93, 166);
        } else if (biome.contains("river")) {
            base = new Color(52, 126, 190);
        } else if (biome.contains("beach")) {
            base = new Color(220, 207, 145);
        } else if (biome.contains("desert") || biome.contains("badlands")) {
            base = biome.contains("badlands") ? new Color(190, 92, 51) : new Color(224, 205, 137);
        } else if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice")) {
            base = new Color(210, 229, 235);
        } else if (biome.contains("jungle") || biome.contains("mangrove")) {
            base = new Color(41, 116, 58);
        } else if (biome.contains("taiga") || biome.contains("grove")) {
            base = new Color(62, 105, 78);
        } else if (biome.contains("forest")) {
            base = new Color(57, 132, 62);
        } else if (biome.contains("mushroom")) {
            base = new Color(151, 78, 145);
        } else if (biome.contains("peak") || biome.contains("slope") || biome.contains("windswept")) {
            base = new Color(132, 139, 137);
        } else if (biome.contains("savanna")) {
            base = new Color(165, 173, 76);
        } else if (biome.contains("swamp")) {
            base = new Color(71, 91, 59);
        } else {
            base = new Color(104, 164, 75);
        }

        double heightShade = Math.max(0.68, Math.min(1.25, 0.84 + (height - 63) / 220.0));
        return new Color(
            clampColor(base.getRed() * heightShade),
            clampColor(base.getGreen() * heightShade),
            clampColor(base.getBlue() * heightShade)
        ).getRGB();
    }

    private static int clampColor(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static int provinceColor(RegionalFieldMath.Province province) {
        return switch (province) {
            case SEDIMENTARY_LOWLAND -> new Color(144, 166, 91).getRGB();
            case WETLAND_BASIN -> new Color(54, 105, 88).getRGB();
            case OLD_ERODED_HIGHLAND -> new Color(117, 112, 83).getRGB();
            case YOUNG_FOLD_MOUNTAINS -> new Color(178, 184, 189).getRGB();
            case GLACIAL_MASSIF -> new Color(193, 224, 235).getRGB();
            case DRY_PLATEAU -> new Color(190, 112, 61).getRGB();
            case VOLCANIC_BELT -> new Color(91, 68, 65).getRGB();
            case KARST_BELT -> new Color(133, 157, 119).getRGB();
            case OCEANIC_CRUST -> new Color(45, 79, 133).getRGB();
            case MYCELIAL_CRATON -> new Color(147, 80, 145).getRGB();
        };
    }

    private static int moodColor(RegionalFieldMath.Mood mood) {
        return switch (mood) {
            case TRANQUIL -> new Color(118, 183, 171).getRGB();
            case PASTORAL -> new Color(170, 193, 104).getRGB();
            case MYSTERIOUS -> new Color(65, 87, 90).getRGB();
            case ANCIENT -> new Color(124, 103, 72).getRGB();
            case MONUMENTAL -> new Color(172, 178, 191).getRGB();
            case DESOLATE -> new Color(157, 137, 115).getRGB();
            case ENCHANTED -> new Color(157, 95, 174).getRGB();
            case DANGEROUS -> new Color(151, 62, 57).getRGB();
            case SACRED -> new Color(222, 198, 116).getRGB();
            case MELANCHOLIC -> new Color(93, 111, 148).getRGB();
        };
    }

    private static int rhythmColor(RegionalFieldMath.Rhythm rhythm) {
        return switch (rhythm) {
            case QUIET -> new Color(77, 127, 151).getRGB();
            case TRANSITIONAL -> new Color(157, 166, 104).getRGB();
            case RECOVERY -> new Color(91, 153, 102).getRGB();
            case DRAMATIC -> new Color(184, 73, 57).getRGB();
        };
    }

    private static int scalarColor(double value, Color low, Color high) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return new Color(
            clampColor(low.getRed() + (high.getRed() - low.getRed()) * t),
            clampColor(low.getGreen() + (high.getGreen() - low.getGreen()) * t),
            clampColor(low.getBlue() + (high.getBlue() - low.getBlue()) * t)
        ).getRGB();
    }

    private static void writeMap(int[][] colors, Path path) throws IOException {
        int sourceSize = colors.length;
        BufferedImage image = new BufferedImage(
            sourceSize * IMAGE_SCALE,
            sourceSize * IMAGE_SCALE,
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            for (int z = 0; z < sourceSize; z++) {
                for (int x = 0; x < sourceSize; x++) {
                    graphics.setColor(new Color(colors[z][x]));
                    graphics.fillRect(
                        x * IMAGE_SCALE,
                        z * IMAGE_SCALE,
                        IMAGE_SCALE,
                        IMAGE_SCALE
                    );
                }
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private record SurveyResult(
        int[][] colors,
        int[][] provinceColors,
        int[][] moodColors,
        int[][] rhythmColors,
        int[][] hierarchyColors,
        int[][] upliftColors,
        Map<String, Integer> biomeCounts,
        Map<String, Integer> provinceCounts,
        Map<String, Integer> moodCounts,
        Map<String, Integer> rhythmCounts,
        int minHeight,
        int maxHeight,
        double meanHeight,
        double standardDeviation,
        double meanSlopePerFourBlocks,
        double waterPercent,
        double meanHierarchyStrength,
        double meanMacroUplift
    ) {
        String report() {
            StringBuilder report = new StringBuilder();
            report.append(String.format(
                Locale.ROOT,
                "height.min=%d%nheight.max=%d%nheight.mean=%.2f%n"
                    + "height.standard_deviation=%.2f%n"
                    + "slope.mean_per_4_blocks=%.2f%nwater.percent=%.2f%n"
                    + "biomes.unique=%d%nprovince.unique=%d%n"
                    + "mood.unique=%d%nrhythm.unique=%d%n"
                    + "hierarchy.mean=%.4f%nmacro_uplift.mean=%.4f%n%n",
                minHeight,
                maxHeight,
                meanHeight,
                standardDeviation,
                meanSlopePerFourBlocks,
                waterPercent,
                biomeCounts.size(),
                provinceCounts.size(),
                moodCounts.size(),
                rhythmCounts.size(),
                meanHierarchyStrength,
                meanMacroUplift
            ));
            biomeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> report.append(String.format(
                    Locale.ROOT,
                    "%s=%d%n",
                    entry.getKey(),
                    entry.getValue()
                )));
            appendCounts(report, "provinces", provinceCounts);
            appendCounts(report, "moods", moodCounts);
            appendCounts(report, "rhythm", rhythmCounts);
            report.append(String.format(Locale.ROOT, "%nfeatures.placed:%n"));
            FEATURE_COUNTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> report.append(String.format(
                    Locale.ROOT,
                    "%s=%d%n",
                    entry.getKey(),
                    entry.getValue().sum()
                )));
            return report.toString();
        }

        private static void appendCounts(
            StringBuilder report,
            String heading,
            Map<String, Integer> counts
        ) {
            report.append(String.format(Locale.ROOT, "%n%s:%n", heading));
            counts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> report.append(String.format(
                    Locale.ROOT,
                    "%s=%d%n",
                    entry.getKey(),
                    entry.getValue()
                )));
        }
    }

    private record RegionalAtlasResult(
        int[][] provinceColors,
        int[][] moodColors,
        int[][] rhythmColors,
        int[][] hierarchyColors,
        int[][] upliftColors,
        Map<String, Integer> provinceCounts,
        Map<String, Integer> moodCounts,
        Map<String, Integer> rhythmCounts,
        double meanHierarchyStrength,
        double meanMacroUplift,
        double dramaticPercent,
        int centerX,
        int centerZ,
        int halfSpan
    ) {
        String report() {
            StringBuilder report = new StringBuilder();
            report.append(String.format(
                Locale.ROOT,
                "center.x=%d%ncenter.z=%d%nspan.min_x=%d%nspan.max_x=%d%n"
                    + "span.min_z=%d%nspan.max_z=%d%nsample.step=%d%n"
                    + "sample.count=%d%nprovince.unique=%d%nmood.unique=%d%n"
                    + "rhythm.unique=%d%nhierarchy.mean=%.4f%n"
                    + "macro_uplift.mean=%.4f%ndramatic.percent=%.2f%n",
                centerX,
                centerZ,
                centerX - halfSpan,
                centerX + halfSpan,
                centerZ - halfSpan,
                centerZ + halfSpan,
                REGIONAL_ATLAS_STEP,
                REGIONAL_ATLAS_SIZE * REGIONAL_ATLAS_SIZE,
                provinceCounts.size(),
                moodCounts.size(),
                rhythmCounts.size(),
                meanHierarchyStrength,
                meanMacroUplift,
                dramaticPercent
            ));
            appendCounts(report, "provinces", provinceCounts);
            appendCounts(report, "moods", moodCounts);
            appendCounts(report, "rhythm", rhythmCounts);
            return report.toString();
        }

        private static void appendCounts(
            StringBuilder report,
            String heading,
            Map<String, Integer> counts
        ) {
            report.append(String.format(Locale.ROOT, "%n%s:%n", heading));
            counts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> report.append(String.format(
                    Locale.ROOT,
                    "%s=%d%n",
                    entry.getKey(),
                    entry.getValue()
                )));
        }
    }
}
