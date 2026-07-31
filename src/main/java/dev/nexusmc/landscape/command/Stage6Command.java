package dev.nexusmc.landscape.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.google.gson.GsonBuilder;
import dev.nexusmc.landscape.NexusLandscape;
import dev.nexusmc.landscape.diagnostics.Stage6Profiler;
import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.NexusV2HydrologySampler;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContext;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContextFactory;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfile;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfileCatalog;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfileResolver;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProvincePass;
import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;
import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfileCatalog;
import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProvincePass;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.fml.ModList;

/**
 * Read-only Stage 6 operator diagnostics. Export is development-gated and
 * disabled by default.
 */
public final class Stage6Command {
    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private Stage6Command() {
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(literal("nexuslandscape")
            .requires(source -> source.hasPermission(
                Stage6CommandPolicy.REQUIRED_PERMISSION_LEVEL
            ))
            .then(literal("debug")
                .then(literal("position")
                    .executes(context -> debugPosition(context.getSource())))
                .then(literal("chunk")
                    .executes(context -> debugChunk(context.getSource())))
                .then(literal("counters")
                    .executes(context -> debugCounters(context.getSource())))
                .then(literal("reset")
                    .executes(context -> debugReset(context.getSource())))
                .then(literal("export")
                    .executes(context -> debugExport(context.getSource()))))
            .then(literal("biome_audit")
                .executes(context -> audit(context.getSource(), 128))
                .then(argument("radius", IntegerArgumentType.integer(16, 512))
                    .executes(context -> audit(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "radius")
                    ))))
            .then(literal("survey")
                .then(argument("radius", IntegerArgumentType.integer(16, 256))
                    .executes(context -> export(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "radius")
                    )))));
    }

    private static int debugPosition(CommandSourceStack source) {
        AuditSample sample = sampleAtSource(source);
        SurfaceContext value = sample.context();
        String cave = caveProfile(source.getLevel(), source);
        String mapping = mappingStatus(value.biomeKey());
        sendLines(source,
            "Nexus Stage 6 position",
            "dimension=" + source.getLevel().dimension().location()
                + " block=" + value.blockX() + ',' + sourceY(source) + ',' + value.blockZ()
                + " chunk=" + Math.floorDiv(value.blockX(), 16) + ',' + Math.floorDiv(value.blockZ(), 16),
            "biome=" + value.biomeKey() + " terrain_region=" + value.terrainProvince(),
            "surface_profile=" + sample.surfaceProfile().profileId()
                + " vegetation_profile=" + sample.vegetationProfile().profileId()
                + " cave_profile=" + cave,
            "zone=" + sample.zone() + " fallback=" + fallbackStatus(value.biomeKey())
                + " natures_spirit=" + mapping,
            formatInfluences(value),
            String.format(Locale.ROOT,
                "slope=%.4f normalized_height=%.4f temperature=%.4f humidity=%.4f",
                value.slope(), value.normalizedHeight(), value.temperature(), value.humidity())
        );
        return 1;
    }

    private static int debugChunk(CommandSourceStack source) {
        AuditSample sample = sampleAtSource(source);
        RandomState state = source.getLevel().getChunkSource().randomState();
        int chunkX = Math.floorDiv(sample.context().blockX(), 16);
        int chunkZ = Math.floorDiv(sample.context().blockZ(), 16);
        sendLines(source,
            "Nexus Stage 6 chunk=" + chunkX + ',' + chunkZ
                + " zone=" + sample.zone(),
            "Counters below are the read-only current-session aggregate; "
                + "the command does not rescan or mutate this chunk.",
            compact(SurfaceProvincePass.snapshot(state)),
            compact(VegetationProvincePass.snapshot(state))
        );
        return 1;
    }

    private static int debugCounters(CommandSourceStack source) {
        RandomState state = source.getLevel().getChunkSource().randomState();
        sendLines(source,
            "Nexus Stage 6 current-session counters",
            compact(SurfaceProvincePass.snapshot(state)),
            compact(VegetationProvincePass.snapshot(state)),
            Stage6Profiler.enabled()
                ? compact(Stage6Profiler.snapshot())
                : "performance=disabled (set NEXUS_LANDSCAPE_PROFILE=1 before launch)"
        );
        return 1;
    }

    private static int debugReset(CommandSourceStack source) {
        RandomState state = source.getLevel().getChunkSource().randomState();
        SurfaceProvincePass.reset(state);
        VegetationProvincePass.reset(state);
        Stage6Profiler.reset();
        source.sendSuccess(() -> Component.literal(
            "Nexus Stage 6 diagnostic counters reset for this generation session."
        ), false);
        return 1;
    }

    private static int debugExport(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AuditSample sample = sampleAtSource(source);
        SurfaceContext value = sample.context();
        RandomState state = level.getChunkSource().randomState();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", 1);
        report.put("created_utc", Instant.now().toString());
        report.put("mod_version", modVersion(NexusLandscape.MOD_ID));
        report.put("build_identifier", implementationVersion());
        report.put("minecraft_version", SharedConstants.getCurrentVersion().getName());
        report.put("neoforge_version", modVersion("neoforge"));
        report.put("seed", level.getSeed());
        report.put("dimension", level.dimension().location().toString());
        report.put("block", Map.of("x", value.blockX(), "y", sourceY(source), "z", value.blockZ()));
        report.put("chunk", Map.of("x", Math.floorDiv(value.blockX(), 16), "z", Math.floorDiv(value.blockZ(), 16)));
        report.put("biome", value.biomeKey());
        report.put("terrain_region", value.terrainProvince());
        report.put("surface_profile", sample.surfaceProfile().profileId());
        report.put("vegetation_profile", sample.vegetationProfile().profileId());
        report.put("cave_profile", caveProfile(level, source));
        report.put("dominant_zone", sample.zone());
        report.put("influences", influenceMap(value));
        report.put("fallback", fallbackStatus(value.biomeKey()));
        report.put("natures_spirit_mapping", mappingStatus(value.biomeKey()));
        report.put("loaded_optional_integrations", loadedIntegrations());
        report.put("surface_counters", parseCounters(SurfaceProvincePass.snapshot(state)));
        report.put("vegetation_and_cave_counters", parseCounters(VegetationProvincePass.snapshot(state)));
        report.put("performance_counters", Stage6Profiler.enabled()
            ? parseCounters(Stage6Profiler.snapshot()) : Map.of("status", "disabled"));
        String timestamp = FILE_TIME.format(Instant.now());
        Path output = Path.of("nexuslandscape-debug", String.format(
            Locale.ROOT, "stage6_%d_%d_%d_%s.json",
            level.getSeed(), value.blockX(), value.blockZ(), timestamp
        ));
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Path absolute = output.toAbsolutePath().normalize();
            source.sendSuccess(() -> Component.literal(
                "Nexus Stage 6 report: " + absolute
            ), false);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal(
                "Nexus Stage 6 export failed: " + exception.getMessage()
            ));
            return 0;
        }
    }

    private static int audit(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        int x = Mth.floor(source.getPosition().x);
        int z = Mth.floor(source.getPosition().z);
        Samplers samplers = new Samplers(level);
        AuditSample current = samplers.sample(x, z);
        Map<String, Integer> coverage = new HashMap<>();
        for (int sampleZ = z - radius; sampleZ <= z + radius; sampleZ += 16) {
            for (int sampleX = x - radius; sampleX <= x + radius; sampleX += 16) {
                String biome = samplers.biomeId(sampleX, sampleZ);
                coverage.merge(biome, 1, Integer::sum);
            }
        }
        source.sendSuccess(() -> Component.literal(String.format(
            Locale.ROOT,
            "Nexus Stage 6 biome_audit r=%d biome=%s province=%s climate=%s "
                + "terrain=%s y=%d slope=%.3f river_distance=%.2f "
                + "river=%.3f lake=%.3f coast=%.3f surface=%s zone=%s "
                + "vegetation=%s optional=%s nearest_landmark=not_indexed "
                + "biomes_in_sample=%d",
            radius,
            current.context().biomeKey(),
            current.context().terrainProvince(),
            current.surfaceProfile().climate().name().toLowerCase(Locale.ROOT),
            current.surfaceProfile().terrain().name().toLowerCase(Locale.ROOT),
            current.context().surfaceY(),
            current.context().slope(),
            current.context().riverDistance(),
            current.context().riverInfluence(),
            current.context().lakeBasinMask(),
            current.context().coastWeight(),
            current.surfaceProfile().profileId(),
            current.zone(),
            current.vegetationProfile().profileId(),
            current.context().biomeKey().startsWith("minecraft:")
                ? "vanilla"
                : "climate_fallback",
            coverage.size()
        )), false);
        return coverage.size();
    }

    private static int export(CommandSourceStack source, int radius) {
        if (!"1".equals(System.getenv("NEXUS_LANDSCAPE_DEV_COMMANDS"))) {
            source.sendFailure(Component.literal(
                "Nexus Stage 6 survey is disabled. "
                    + "Set NEXUS_LANDSCAPE_DEV_COMMANDS=1 before server start."
            ));
            return 0;
        }
        ServerLevel level = source.getLevel();
        int centerX = Mth.floor(source.getPosition().x);
        int centerZ = Mth.floor(source.getPosition().z);
        Samplers samplers = new Samplers(level);
        StringBuilder csv = new StringBuilder(
            "seed,x,z,y,biome,province,surface_profile,surface_zone,"
                + "vegetation_profile,slope,river,lake,coast\n"
        );
        int rows = 0;
        for (int z = centerZ - radius; z <= centerZ + radius; z += 8) {
            for (int x = centerX - radius; x <= centerX + radius; x += 8) {
                AuditSample sample = samplers.sample(x, z);
                SurfaceContext context = sample.context();
                csv.append(level.getSeed()).append(',')
                    .append(x).append(',').append(z).append(',')
                    .append(context.surfaceY()).append(',')
                    .append(context.biomeKey()).append(',')
                    .append(context.terrainProvince()).append(',')
                    .append(sample.surfaceProfile().profileId()).append(',')
                    .append(sample.zone()).append(',')
                    .append(sample.vegetationProfile().profileId()).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", context.slope())).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", context.riverInfluence())).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", context.lakeBasinMask())).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", context.coastWeight()))
                    .append('\n');
                rows++;
            }
        }
        try {
            Path output = Path.of(
                "..",
                "build",
                "reports",
                "nexus-worldgen",
                "stage6-command-survey-"
                    + level.getSeed() + '-' + centerX + '-' + centerZ + ".csv"
            );
            Files.createDirectories(output.getParent());
            Files.writeString(output, csv);
            int finalRows = rows;
            source.sendSuccess(() -> Component.literal(
                "Nexus Stage 6 survey rows=" + finalRows
                    + " output=" + output.toAbsolutePath().normalize()
            ), false);
            return rows;
        } catch (IOException exception) {
            source.sendFailure(Component.literal(
                "Nexus Stage 6 survey failed: " + exception.getMessage()
            ));
            return 0;
        }
    }

    private static AuditSample sampleAtSource(CommandSourceStack source) {
        return new Samplers(source.getLevel()).sample(
            Mth.floor(source.getPosition().x),
            Mth.floor(source.getPosition().z)
        );
    }

    private static int sourceY(CommandSourceStack source) {
        return Mth.floor(source.getPosition().y);
    }

    private static String caveProfile(
        ServerLevel level,
        CommandSourceStack source
    ) {
        int x = Mth.floor(source.getPosition().x);
        int y = sourceY(source);
        int z = Mth.floor(source.getPosition().z);
        String biome = level.getBiome(new net.minecraft.core.BlockPos(x, y, z))
            .unwrapKey()
            .map(key -> key.location().toString())
            .orElse("nexus_landscape:unknown");
        return VegetationProfileCatalog.find(biome)
            .filter(profile -> !profile.terrestrial())
            .map(VegetationProfile::profileId)
            .orElse("nexus_landscape:generic_cave_fallback");
    }

    private static String fallbackStatus(String biomeId) {
        return SurfaceProfileCatalog.find(biomeId).isPresent()
            && VegetationProfileCatalog.find(biomeId).isPresent()
            ? "explicit"
            : "climate_fallback";
    }

    private static String mappingStatus(String biomeId) {
        if (!biomeId.startsWith("natures_spirit:")) {
            return ModList.get().isLoaded("natures_spirit")
                ? "loaded_not_current_biome"
                : "not_loaded";
        }
        return fallbackStatus(biomeId);
    }

    private static String formatInfluences(SurfaceContext value) {
        return String.format(Locale.ROOT,
            "river=%.4f lake=%.4f wet_bank=%.4f coast=%.4f volcanic=%.4f alpine=%.4f",
            value.riverInfluence(), value.lakeBasinMask(), wetBank(value),
            value.coastWeight(), value.volcanicWeight(), value.alpineInfluence());
    }

    private static Map<String, Double> influenceMap(SurfaceContext value) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("river", value.riverInfluence());
        result.put("river_mask", value.riverMask());
        result.put("lake", value.lakeBasinMask());
        result.put("wet_bank", wetBank(value));
        result.put("coast", value.coastWeight());
        result.put("volcanic", value.volcanicWeight());
        result.put("alpine", value.alpineInfluence());
        result.put("slope", value.slope());
        result.put("normalized_height", value.normalizedHeight());
        result.put("temperature", value.temperature());
        result.put("humidity", value.humidity());
        return result;
    }

    private static double wetBank(SurfaceContext value) {
        double distance = 1.0 - Mth.clamp(value.riverDistance() / 24.0, 0.0, 1.0);
        return Math.max(distance, Math.max(value.groundwater(), value.lakeBasinMask()));
    }

    private static Map<String, Object> parseCounters(String counters) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String line : counters.lines().toList()) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                result.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return result;
    }

    private static String compact(String counters) {
        return counters.replace('\n', ' ').trim();
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("not_loaded");
    }

    private static String implementationVersion() {
        String value = Stage6Command.class.getPackage().getImplementationVersion();
        return value == null ? "development_or_unstamped" : value;
    }

    private static Map<String, String> loadedIntegrations() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : new String[] {"natures_spirit", "nexus_mobs", "sodium"}) {
            if (ModList.get().isLoaded(id)) {
                result.put(id, modVersion(id));
            }
        }
        return result;
    }

    private static void sendLines(CommandSourceStack source, String... lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    private static final class Samplers {
        private final ServerLevel level;
        private final NexusV2FieldSampler fields;
        private final NexusV2HydrologySampler hydrology;

        private Samplers(ServerLevel level) {
            this.level = level;
            RandomState randomState = level.getChunkSource().randomState();
            this.fields = new NexusV2FieldSampler(randomState);
            this.hydrology = new NexusV2HydrologySampler(randomState);
        }

        private String biomeId(int x, int z) {
            int y = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                x,
                z
            ) - 1;
            return level.getBiome(new net.minecraft.core.BlockPos(x, y, z))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("nexus_landscape:unknown");
        }

        private AuditSample sample(int x, int z) {
            int y = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                x,
                z
            ) - 1;
            String biomeId = biomeId(x, z);
            NexusV2FieldSampler.SurfaceInputs input = fields.surfaceInputs(x, z);
            RegionalFieldMath.Sample regional =
                SurfaceContextFactory.regional(input);
            HydrologyMath.Sample river = hydrology.sample(x, z);
            HydrologyMath.BasinSample basin = hydrology.basinSample(x, z);
            double dx = fields.analyticalTerrain(x + 4, z).surfaceY()
                - fields.analyticalTerrain(x - 4, z).surfaceY();
            double dz = fields.analyticalTerrain(x, z + 4).surfaceY()
                - fields.analyticalTerrain(x, z - 4).surfaceY();
            double slope = Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / 32.0);
            SurfaceContext context = SurfaceContextFactory.create(
                level.getSeed(), x, z, y, biomeId, input, regional,
                river, basin, slope
            );
            SurfaceProfile surface = SurfaceProfileCatalog.find(biomeId)
                .filter(profile -> profile.elevation()
                    != SurfaceProfile.ElevationBand.SUBTERRANEAN)
                .orElseGet(() -> SurfaceProfileCatalog.fallback(
                    input.temperature(), input.humidity(), false
                ));
            VegetationProfile vegetation =
                VegetationProfileCatalog.find(biomeId)
                    .filter(VegetationProfile::terrestrial)
                    .orElseGet(() -> VegetationProfileCatalog.fallback(
                        input.temperature(), input.humidity(), false
                    ));
            return new AuditSample(
                context,
                surface,
                SurfaceProfileResolver.resolve(surface, context).zone()
                    .name().toLowerCase(Locale.ROOT),
                vegetation
            );
        }
    }

    private record AuditSample(
        SurfaceContext context,
        SurfaceProfile surfaceProfile,
        String zone,
        VegetationProfile vegetationProfile
    ) {
    }
}
