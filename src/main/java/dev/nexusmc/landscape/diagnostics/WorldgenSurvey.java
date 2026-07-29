package dev.nexusmc.landscape.diagnostics;

import com.mojang.logging.LogUtils;
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
            writeMap(result, output.resolve("survey.png"));
            Files.writeString(output.resolve("survey.txt"), result.report());
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
        Map<String, Integer> biomeCounts = new HashMap<>();
        long heightSum = 0;
        long heightSquareSum = 0;
        long slopeSum = 0;
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

                heights[imageZ][imageX] = height;
                colors[imageZ][imageX] = biomeColor(biomeName, height, water);
                biomeCounts.merge(biomeName, 1, Integer::sum);
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
                heightSum += height;
                heightSquareSum += (long) height * height;
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
            biomeCounts,
            minHeight,
            maxHeight,
            mean,
            Math.sqrt(Math.max(0.0, variance)),
            slopeSum / (double) slopeSamples,
            waterSamples * 100.0 / sampleCount
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

    private static void writeMap(SurveyResult result, Path path) throws IOException {
        int sourceSize = result.colors().length;
        BufferedImage image = new BufferedImage(
            sourceSize * IMAGE_SCALE,
            sourceSize * IMAGE_SCALE,
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            for (int z = 0; z < sourceSize; z++) {
                for (int x = 0; x < sourceSize; x++) {
                    graphics.setColor(new Color(result.colors()[z][x]));
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
        Map<String, Integer> biomeCounts,
        int minHeight,
        int maxHeight,
        double meanHeight,
        double standardDeviation,
        double meanSlopePerFourBlocks,
        double waterPercent
    ) {
        String report() {
            StringBuilder report = new StringBuilder();
            report.append(String.format(
                Locale.ROOT,
                "height.min=%d%nheight.max=%d%nheight.mean=%.2f%n"
                    + "height.standard_deviation=%.2f%n"
                    + "slope.mean_per_4_blocks=%.2f%nwater.percent=%.2f%n"
                    + "biomes.unique=%d%n%n",
                minHeight,
                maxHeight,
                meanHeight,
                standardDeviation,
                meanSlopePerFourBlocks,
                waterPercent,
                biomeCounts.size()
            ));
            biomeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> report.append(String.format(
                    Locale.ROOT,
                    "%s=%d%n",
                    entry.getKey(),
                    entry.getValue()
                )));
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
    }
}
