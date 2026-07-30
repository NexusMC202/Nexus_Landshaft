package dev.nexusmc.landscape.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyMath;
import dev.nexusmc.landscape.worldgen.v2.hydrology.NexusV2HydrologySampler;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContext;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceContextFactory;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfile;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfileCatalog;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProfileResolver;
import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfile;
import dev.nexusmc.landscape.worldgen.v2.vegetation.VegetationProfileCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Read-only Stage 6 operator diagnostics. Export is development-gated and
 * disabled by default.
 */
public final class Stage6Command {
    private Stage6Command() {
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(literal("nexuslandscape")
            .requires(source -> source.hasPermission(2))
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
