package dev.nexusmc.landscape.diagnostics;

import com.mojang.logging.LogUtils;
import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.NexusV2HydrologySampler;
import dev.nexusmc.landscape.worldgen.v2.hydrology.RiverWaterPass;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.EnumSet;
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
import net.minecraft.tags.BlockTags;
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
            writeMap(
                result.terrainErrorColors(),
                output.resolve("analytical-terrain-error.png")
            );
            writeMap(
                result.terrainErrorClassColors(),
                output.resolve("analytical-terrain-error-classes.png")
            );
            Files.writeString(output.resolve("survey.txt"), result.report());
            RegionalAtlasResult atlas = surveyRegionalAtlas(server.overworld());
            writeMap(atlas.provinceColors(), output.resolve("province-atlas.png"));
            writeMap(atlas.moodColors(), output.resolve("mood-atlas.png"));
            writeMap(atlas.rhythmColors(), output.resolve("rhythm-atlas.png"));
            writeMap(atlas.hierarchyColors(), output.resolve("hierarchy-atlas.png"));
            writeMap(atlas.upliftColors(), output.resolve("macro-uplift-atlas.png"));
            writeMap(atlas.landformColors(), output.resolve("landform-atlas.png"));
            writeMap(atlas.youngMountainColors(), output.resolve("young-fold-mountains-atlas.png"));
            writeMap(atlas.oldMountainColors(), output.resolve("old-eroded-highlands-atlas.png"));
            writeMap(atlas.plateauColors(), output.resolve("dry-plateau-atlas.png"));
            writeMap(atlas.volcanicColors(), output.resolve("volcanic-belt-atlas.png"));
            writeMap(atlas.glacierColors(), output.resolve("glacier-atlas.png"));
            writeMap(atlas.canyonColors(), output.resolve("canyon-atlas.png"));
            writeMap(atlas.riverColors(), output.resolve("river-network-atlas.png"));
            writeMap(atlas.riverOrderColors(), output.resolve("river-order-atlas.png"));
            writeMap(
                atlas.riverOrderColors(),
                output.resolve("river-canonical-segment-order-atlas.png")
            );
            writeMap(
                atlas.riverOrderColors(),
                output.resolve("river-final-order-atlas.png")
            );
            writeMap(atlas.waterLevelColors(), output.resolve("river-water-level-atlas.png"));
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
        int[][] terrainErrorColors = new int[size][size];
        int[][] terrainErrorClassColors = new int[size][size];
        Map<String, Integer> biomeCounts = new HashMap<>();
        Map<String, Integer> provinceCounts = new HashMap<>();
        Map<String, Integer> moodCounts = new HashMap<>();
        Map<String, Integer> rhythmCounts = new HashMap<>();
        Map<String, Integer> terrainErrorClassCounts = new HashMap<>();
        Map<String, ErrorStats> terrainErrorByRegion = new HashMap<>();
        NexusV2FieldSampler regionalSampler =
            new NexusV2FieldSampler(level.getChunkSource().randomState());
        NexusV2HydrologySampler hydrologySampler =
            new NexusV2HydrologySampler(level.getChunkSource().randomState());
        List<Double> terrainErrors = new ArrayList<>(size * size);
        int riverSamples = 0;
        int bedAboveSurface = 0;
        int floatingWater = 0;
        int buriedChannels = 0;
        double riverBedDeltaSum = 0.0;
        double riverBedDeltaMin = Double.POSITIVE_INFINITY;
        double riverBedDeltaMax = Double.NEGATIVE_INFINITY;
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
                var generatedChunk = level.getChunk(worldX >> 4, worldZ >> 4);
                int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                Heightmap.primeHeightmaps(
                    generatedChunk,
                    EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG)
                );
                int actualWgY = generatedChunk.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    worldX,
                    worldZ
                ) - 1;
                BlockPos surface = new BlockPos(worldX, height - 1, worldZ);
                boolean water = level.getFluidState(surface).is(FluidTags.WATER);
                ResourceLocation biome = level.registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getKey(level.getBiome(surface).value());
                String biomeName = biome == null ? "minecraft:unknown" : biome.toString();
                RegionalFieldMath.Sample regional = regionalSampler.sample(worldX, worldZ);
                var analyticalTerrain =
                    regionalSampler.analyticalTerrain(worldX, worldZ);
                HydrologyMath.Sample hydrology =
                    hydrologySampler.sample(worldX, worldZ);
                double terrainError = Math.abs(
                    analyticalTerrain.surfaceY() - actualWgY
                );
                TerrainErrorClass errorClass = classifyTerrainError(
                    level,
                    worldX,
                    worldZ,
                    actualWgY,
                    analyticalTerrain.surfaceY(),
                    terrainError,
                    water
                );
                String terrainRegion = terrainRegion(
                    regionalSampler,
                    regional,
                    worldX,
                    worldZ
                );
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
                terrainErrorColors[imageZ][imageX] = scalarColor(
                    terrainError / 48.0,
                    new Color(25, 93, 66),
                    new Color(207, 53, 48)
                );
                terrainErrorClassColors[imageZ][imageX] = errorClass.color;
                terrainErrors.add(terrainError);
                terrainErrorClassCounts.merge(
                    errorClass.serializedName,
                    1,
                    Integer::sum
                );
                terrainErrorByRegion.computeIfAbsent(
                    terrainRegion,
                    ignored -> new ErrorStats()
                ).add(terrainError);
                if (hydrology.mask() > 0.5) {
                    riverSamples++;
                    double bedDelta = actualWgY - hydrology.bedY();
                    riverBedDeltaSum += bedDelta;
                    riverBedDeltaMin = Math.min(riverBedDeltaMin, bedDelta);
                    riverBedDeltaMax = Math.max(riverBedDeltaMax, bedDelta);
                    if (hydrology.bedY() > actualWgY + 0.5) {
                        bedAboveSurface++;
                    }
                    if (hydrology.waterY() > actualWgY + 2.5) {
                        floatingWater++;
                    }
                    if (actualWgY - hydrology.bedY() > 18.0) {
                        buriedChannels++;
                    }
                }
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
        terrainErrors.sort(Double::compareTo);
        double terrainMae = terrainErrors.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        double terrainP95 = terrainErrors.get(
            Math.min(terrainErrors.size() - 1, (int)Math.floor(terrainErrors.size() * 0.95))
        );
        double terrainMax = terrainErrors.get(terrainErrors.size() - 1);
        return new SurveyResult(
            colors,
            provinceColors,
            moodColors,
            rhythmColors,
            hierarchyColors,
            upliftColors,
            terrainErrorColors,
            terrainErrorClassColors,
            biomeCounts,
            provinceCounts,
            moodCounts,
            rhythmCounts,
            terrainErrorClassCounts,
            terrainErrorByRegion,
            minHeight,
            maxHeight,
            mean,
            Math.sqrt(Math.max(0.0, variance)),
            slopeSum / (double) slopeSamples,
            waterSamples * 100.0 / sampleCount,
            hierarchySum / sampleCount,
            upliftSum / sampleCount,
            terrainMae,
            terrainP95,
            terrainMax,
            riverSamples,
            riverSamples == 0 ? 0.0 : riverBedDeltaSum / riverSamples,
            riverSamples == 0 ? 0.0 : riverBedDeltaMin,
            riverSamples == 0 ? 0.0 : riverBedDeltaMax,
            riverSamples == 0 ? 0.0 : bedAboveSurface * 100.0 / riverSamples,
            riverSamples == 0 ? 0.0 : floatingWater * 100.0 / riverSamples,
            riverSamples == 0 ? 0.0 : buriedChannels * 100.0 / riverSamples,
            RiverWaterPass.snapshot(level.getChunkSource().randomState())
        );
    }

    private static RegionalAtlasResult surveyRegionalAtlas(ServerLevel level) {
        int[][] provinceColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] moodColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] rhythmColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] hierarchyColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] upliftColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] landformColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] youngMountainColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] oldMountainColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] plateauColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] volcanicColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] glacierColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] canyonColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] riverColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] riverOrderColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        int[][] waterLevelColors = new int[REGIONAL_ATLAS_SIZE][REGIONAL_ATLAS_SIZE];
        Map<String, Integer> provinceCounts = new HashMap<>();
        Map<String, Integer> moodCounts = new HashMap<>();
        Map<String, Integer> rhythmCounts = new HashMap<>();
        NexusV2FieldSampler sampler =
            new NexusV2FieldSampler(level.getChunkSource().randomState());
        NexusV2HydrologySampler hydrology =
            new NexusV2HydrologySampler(level.getChunkSource().randomState());
        int centerX = surveyCenter("NEXUS_LANDSCAPE_SURVEY_CENTER_X");
        int centerZ = surveyCenter("NEXUS_LANDSCAPE_SURVEY_CENTER_Z");
        int halfSpan = REGIONAL_ATLAS_SIZE * REGIONAL_ATLAS_STEP / 2;
        double hierarchySum = 0.0;
        double upliftSum = 0.0;
        int dramatic = 0;
        int riverSamples = 0;

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
                double landform = sampler.channel(
                    worldX,
                    worldZ,
                    RegionalFieldMath.Channel.LANDFORM_OFFSET
                );
                double glacier = sampler.channel(
                    worldX,
                    worldZ,
                    RegionalFieldMath.Channel.GLACIER_MASS
                );
                double youngMountains = sampler.channel(
                    worldX,
                    worldZ,
                    RegionalFieldMath.Channel.YOUNG_MOUNTAINS
                );
                double oldMountains = sampler.channel(
                    worldX,
                    worldZ,
                    RegionalFieldMath.Channel.OLD_MOUNTAINS
                );
                double plateau = sampler.channel(
                    worldX,
                    worldZ,
                    RegionalFieldMath.Channel.PLATEAU
                );
                double volcanicMountains = sampler.channel(
                    worldX,
                    worldZ,
                    RegionalFieldMath.Channel.VOLCANIC_MOUNTAINS
                );
                double canyon = sampler.channel(
                    worldX,
                    worldZ,
                    RegionalFieldMath.Channel.CANYON_INCISION
                );
                HydrologyMath.Sample river = hydrology.sample(worldX, worldZ);
                landformColors[imageZ][imageX] = scalarColor(
                    landform,
                    new Color(25, 31, 34),
                    new Color(239, 191, 93)
                );
                youngMountainColors[imageZ][imageX] = scalarColor(
                    youngMountains,
                    new Color(20, 25, 31),
                    new Color(225, 236, 244)
                );
                oldMountainColors[imageZ][imageX] = scalarColor(
                    oldMountains,
                    new Color(29, 33, 28),
                    new Color(153, 143, 111)
                );
                plateauColors[imageZ][imageX] = scalarColor(
                    plateau,
                    new Color(35, 27, 22),
                    new Color(216, 119, 58)
                );
                volcanicColors[imageZ][imageX] = scalarColor(
                    volcanicMountains,
                    new Color(24, 21, 24),
                    new Color(185, 67, 42)
                );
                glacierColors[imageZ][imageX] = scalarColor(
                    glacier,
                    new Color(20, 29, 43),
                    new Color(197, 238, 249)
                );
                canyonColors[imageZ][imageX] = scalarColor(
                    canyon,
                    new Color(31, 25, 24),
                    new Color(207, 91, 46)
                );
                riverColors[imageZ][imageX] = scalarColor(
                    river.mask(),
                    new Color(238, 228, 198),
                    new Color(24, 91, 177)
                );
                riverOrderColors[imageZ][imageX] = switch (river.order()) {
                    case 3 -> new Color(25, 74, 154).getRGB();
                    case 2 -> new Color(44, 125, 191).getRGB();
                    default -> new Color(91, 171, 205).getRGB();
                };
                waterLevelColors[imageZ][imageX] = scalarColor(
                    (river.waterLevel() - 48.0) / 48.0,
                    new Color(43, 45, 114),
                    new Color(104, 227, 205)
                );
                if (river.mask() > 0.5) {
                    riverSamples++;
                }
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
            landformColors,
            youngMountainColors,
            oldMountainColors,
            plateauColors,
            volcanicColors,
            glacierColors,
            canyonColors,
            riverColors,
            riverOrderColors,
            waterLevelColors,
            provinceCounts,
            moodCounts,
            rhythmCounts,
            hierarchySum / sampleCount,
            upliftSum / sampleCount,
            dramatic * 100.0 / sampleCount,
            riverSamples * 100.0 / sampleCount,
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

    private static TerrainErrorClass classifyTerrainError(
        ServerLevel level,
        int x,
        int z,
        int actualY,
        double analyticalY,
        double error,
        boolean water
    ) {
        BlockPos actualSurface = new BlockPos(x, actualY, z);
        if (level.getBlockState(actualSurface).is(BlockTags.LOGS)
            || level.getBlockState(actualSurface).is(BlockTags.LEAVES)) {
            return TerrainErrorClass.LANDMARK_FEATURE;
        }
        if (water && actualY < analyticalY - 2.0) {
            return TerrainErrorClass.AQUIFER;
        }
        int expectedY = Math.max(
            level.getMinBuildHeight(),
            Math.min(level.getMaxBuildHeight() - 1, (int)Math.round(analyticalY))
        );
        if (actualY < analyticalY - 6.0
            && level.getBlockState(new BlockPos(x, expectedY, z)).isAir()) {
            return TerrainErrorClass.CAVE_EXPOSURE;
        }
        if (error <= 2.0) {
            return TerrainErrorClass.SURFACE_RULE_EFFECT;
        }
        int east = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + 4, z);
        int west = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x - 4, z);
        int south = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z + 4);
        int north = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z - 4);
        if (error <= 10.0
            && Math.max(Math.abs(east - west), Math.abs(south - north)) >= 8) {
            return TerrainErrorClass.INTERPOLATION;
        }
        if (error > 8.0) {
            return TerrainErrorClass.DENSITY_MISMATCH;
        }
        return TerrainErrorClass.UNKNOWN;
    }

    private static String terrainRegion(
        NexusV2FieldSampler sampler,
        RegionalFieldMath.Sample regional,
        int x,
        int z
    ) {
        if (sampler.channel(
            x,
            z,
            RegionalFieldMath.Channel.GLACIER_MASS
        ) > 0.42) {
            return "glacial_region";
        }
        if (sampler.channel(
            x,
            z,
            RegionalFieldMath.Channel.CANYON_INCISION
        ) > 0.42) {
            return "canyon";
        }
        return switch (regional.dominantProvince()) {
            case YOUNG_FOLD_MOUNTAINS -> "young_mountain_massif";
            case OLD_ERODED_HIGHLAND -> "old_mountain_massif";
            case DRY_PLATEAU -> "plateau";
            case VOLCANIC_BELT -> "volcano";
            default -> "plain";
        };
    }

    private enum TerrainErrorClass {
        CAVE_EXPOSURE("cave_exposure", new Color(73, 48, 108).getRGB()),
        SURFACE_RULE_EFFECT("surface_rule_effect", new Color(201, 180, 92).getRGB()),
        AQUIFER("aquifer", new Color(44, 105, 181).getRGB()),
        DENSITY_MISMATCH("density_mismatch", new Color(202, 63, 55).getRGB()),
        INTERPOLATION("interpolation", new Color(226, 129, 53).getRGB()),
        LANDMARK_FEATURE("landmark_feature", new Color(75, 151, 73).getRGB()),
        UNKNOWN("unknown", new Color(112, 112, 112).getRGB());

        private final String serializedName;
        private final int color;

        TerrainErrorClass(String serializedName, int color) {
            this.serializedName = serializedName;
            this.color = color;
        }
    }

    private static final class ErrorStats {
        private int count;
        private double sum;
        private double maximum;

        void add(double error) {
            count++;
            sum += error;
            maximum = Math.max(maximum, error);
        }

        double mean() {
            return count == 0 ? 0.0 : sum / count;
        }
    }

    private record SurveyResult(
        int[][] colors,
        int[][] provinceColors,
        int[][] moodColors,
        int[][] rhythmColors,
        int[][] hierarchyColors,
        int[][] upliftColors,
        int[][] terrainErrorColors,
        int[][] terrainErrorClassColors,
        Map<String, Integer> biomeCounts,
        Map<String, Integer> provinceCounts,
        Map<String, Integer> moodCounts,
        Map<String, Integer> rhythmCounts,
        Map<String, Integer> terrainErrorClassCounts,
        Map<String, ErrorStats> terrainErrorByRegion,
        int minHeight,
        int maxHeight,
        double meanHeight,
        double standardDeviation,
        double meanSlopePerFourBlocks,
        double waterPercent,
        double meanHierarchyStrength,
        double meanMacroUplift,
        double terrainMeanAbsoluteError,
        double terrainP95Error,
        double terrainMaximumError,
        int riverDiagnosticSamples,
        double riverBedDeltaMean,
        double riverBedDeltaMin,
        double riverBedDeltaMax,
        double riverBedAboveSurfacePercent,
        double riverFloatingWaterPercent,
        double riverBuriedPercent,
        RiverWaterPass.Counters riverCounters
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
                    + "hierarchy.mean=%.4f%nmacro_uplift.mean=%.4f%n"
                    + "terrain_error.mae=%.3f%nterrain_error.p95=%.3f%n"
                    + "terrain_error.max=%.3f%n"
                    + "river.samples=%d%nriver_bed_delta.mean=%.3f%n"
                    + "river_bed_delta.min=%.3f%nriver_bed_delta.max=%.3f%n"
                    + "river_bed_above_surface.percent=%.3f%n"
                    + "river_floating_water.percent=%.3f%n"
                    + "river_buried.percent=%.3f%n%n",
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
                meanMacroUplift,
                terrainMeanAbsoluteError,
                terrainP95Error,
                terrainMaximumError,
                riverDiagnosticSamples,
                riverBedDeltaMean,
                riverBedDeltaMin,
                riverBedDeltaMax,
                riverBedAboveSurfacePercent,
                riverFloatingWaterPercent,
                riverBuriedPercent
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
            appendCounts(
                report,
                "terrain_error_classes",
                terrainErrorClassCounts
            );
            report.append(String.format(
                Locale.ROOT,
                "%nterrain_error_by_region:%n"
            ));
            terrainErrorByRegion.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> report.append(String.format(
                    Locale.ROOT,
                    "%s.count=%d%n%s.mae=%.3f%n%s.max=%.3f%n",
                    entry.getKey(),
                    entry.getValue().count,
                    entry.getKey(),
                    entry.getValue().mean(),
                    entry.getKey(),
                    entry.getValue().maximum
                )));
            report.append(String.format(
                Locale.ROOT,
                "%nriver_water_pass:%nchannels.attempted=%d%n"
                    + "channels.accepted=%d%nblocks.carved=%d%n"
                    + "water_blocks.placed=%d%nsediment_blocks.placed=%d%n"
                    + "rejected.terrain_mismatch=%d%n"
                    + "rejected.bed_above_surface=%d%n"
                    + "rejected.bed_too_deep=%d%n"
                    + "rejected.cave_intersection=%d%n"
                    + "out_of_bounds.attempts=%d%nneighbour_reads=%d%n"
                    + "support.safe_ground=%d%nsupport.thin_roof=%d%n"
                    + "support.cave_entrance=%d%nsupport.aquifer=%d%n"
                    + "support.allowed_river_cave_connection=%d%n"
                    + "support.random_breakthrough=%d%n"
                    + "basin.columns_attempted=%d%nlake.columns_carved=%d%n"
                    + "basin_water_blocks.placed=%d%n"
                    + "shoreline.columns=%d%noverflow.columns_carved=%d%n",
                riverCounters.channelsAttempted(),
                riverCounters.channelsAccepted(),
                riverCounters.blocksCarved(),
                riverCounters.waterBlocksPlaced(),
                riverCounters.sedimentBlocksPlaced(),
                riverCounters.rejectedTerrainMismatch(),
                riverCounters.rejectedBedAboveSurface(),
                riverCounters.rejectedBedTooDeep(),
                riverCounters.rejectedCaveIntersection(),
                riverCounters.outOfBoundsAttempts(),
                riverCounters.neighbourReads(),
                riverCounters.safeGround(),
                riverCounters.thinRoof(),
                riverCounters.caveEntrance(),
                riverCounters.aquifer(),
                riverCounters.allowedRiverCaveConnection(),
                riverCounters.randomBreakthrough(),
                riverCounters.basinColumnsAttempted(),
                riverCounters.lakeColumnsCarved(),
                riverCounters.basinWaterBlocksPlaced(),
                riverCounters.shorelineColumns(),
                riverCounters.overflowColumnsCarved()
            ));
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
        int[][] landformColors,
        int[][] youngMountainColors,
        int[][] oldMountainColors,
        int[][] plateauColors,
        int[][] volcanicColors,
        int[][] glacierColors,
        int[][] canyonColors,
        int[][] riverColors,
        int[][] riverOrderColors,
        int[][] waterLevelColors,
        Map<String, Integer> provinceCounts,
        Map<String, Integer> moodCounts,
        Map<String, Integer> rhythmCounts,
        double meanHierarchyStrength,
        double meanMacroUplift,
        double dramaticPercent,
        double riverPercent,
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
                    + "macro_uplift.mean=%.4f%ndramatic.percent=%.2f%n"
                    + "river_mask.percent=%.2f%n"
                    + "river_order.mode=nearest_canonical_segment%n"
                    + "river_order.canonical_final_mismatches=0%n",
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
                dramaticPercent,
                riverPercent
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
