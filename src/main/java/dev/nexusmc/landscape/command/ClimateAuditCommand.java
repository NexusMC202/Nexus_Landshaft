package dev.nexusmc.landscape.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Non-loading climate and biome reachability diagnostics for Alpha testing.
 * Samples the configured BiomeSource directly, so it does not generate chunks.
 */
public final class ClimateAuditCommand {
    private static final int DEFAULT_RADIUS = 8192;
    private static final int DEFAULT_STEP = 128;
    private static final long MAX_SAMPLES = 100_000L;

    private ClimateAuditCommand() {
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(literal("nexusclimate")
            .requires(source -> source.hasPermission(
                Stage6CommandPolicy.REQUIRED_PERMISSION_LEVEL
            ))
            .then(literal("audit")
                .executes(context -> audit(
                    context.getSource(), DEFAULT_RADIUS, DEFAULT_STEP
                ))
                .then(argument(
                    "radius",
                    IntegerArgumentType.integer(512, 16_384)
                ).executes(context -> audit(
                    context.getSource(),
                    IntegerArgumentType.getInteger(context, "radius"),
                    DEFAULT_STEP
                )).then(argument(
                    "step",
                    IntegerArgumentType.integer(32, 512)
                ).executes(context -> audit(
                    context.getSource(),
                    IntegerArgumentType.getInteger(context, "radius"),
                    IntegerArgumentType.getInteger(context, "step")
                ))))));
    }

    private static int audit(
        CommandSourceStack source,
        int radius,
        int step
    ) {
        long side = (radius * 2L) / step + 1L;
        long requestedSamples = side * side;
        if (requestedSamples > MAX_SAMPLES) {
            source.sendFailure(Component.literal(String.format(
                Locale.ROOT,
                "Climate audit refused: %,d samples exceed the %,d limit. "
                    + "Increase step or reduce radius.",
                requestedSamples,
                MAX_SAMPLES
            )));
            return 0;
        }

        ServerLevel level = source.getLevel();
        RandomState randomState = level.getChunkSource().randomState();
        BiomeSource biomeSource = level.getChunkSource()
            .getGenerator()
            .getBiomeSource();
        NexusV2FieldSampler fields = new NexusV2FieldSampler(randomState);
        int centerX = Mth.floor(source.getPosition().x);
        int centerZ = Mth.floor(source.getPosition().z);
        int quartY = QuartPos.fromBlock(64);

        Map<String, Integer> biomes = new HashMap<>();
        double minTemperature = Double.POSITIVE_INFINITY;
        double maxTemperature = Double.NEGATIVE_INFINITY;
        double minHumidity = Double.POSITIVE_INFINITY;
        double maxHumidity = Double.NEGATIVE_INFINITY;
        int hotDryCandidates = 0;
        int desert = 0;
        int savanna = 0;
        int badlands = 0;
        int samples = 0;

        for (int z = centerZ - radius; z <= centerZ + radius; z += step) {
            for (int x = centerX - radius; x <= centerX + radius; x += step) {
                NexusV2FieldSampler.SurfaceInputs climate =
                    fields.surfaceInputs(x, z);
                double temperature = climate.temperature();
                double humidity = climate.humidity();
                minTemperature = Math.min(minTemperature, temperature);
                maxTemperature = Math.max(maxTemperature, temperature);
                minHumidity = Math.min(minHumidity, humidity);
                maxHumidity = Math.max(maxHumidity, humidity);
                if (temperature >= 0.55 && humidity <= -0.30) {
                    hotDryCandidates++;
                }

                Holder<Biome> biome = biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(x),
                    quartY,
                    QuartPos.fromBlock(z),
                    randomState.sampler()
                );
                String biomeId = biome.unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("nexus_landscape:unknown");
                biomes.merge(biomeId, 1, Integer::sum);
                if (biomeId.equals("minecraft:desert")) {
                    desert++;
                }
                if (biomeId.equals("minecraft:savanna")
                    || biomeId.equals("minecraft:savanna_plateau")
                    || biomeId.equals("minecraft:windswept_savanna")) {
                    savanna++;
                }
                if (biomeId.equals("minecraft:badlands")
                    || biomeId.equals("minecraft:eroded_badlands")
                    || biomeId.equals("minecraft:wooded_badlands")) {
                    badlands++;
                }
                samples++;
            }
        }

        send(source, String.format(
            Locale.ROOT,
            "Nexus climate audit seed=%d center=%d,%d radius=%d step=%d "
                + "samples=%d unique_biomes=%d",
            level.getSeed(), centerX, centerZ, radius, step,
            samples, biomes.size()
        ));
        send(source, String.format(
            Locale.ROOT,
            "climate temperature=[%.4f, %.4f] humidity=[%.4f, %.4f] "
                + "hot_dry_candidates=%d",
            minTemperature, maxTemperature, minHumidity, maxHumidity,
            hotDryCandidates
        ));
        send(source, String.format(
            Locale.ROOT,
            "hot_biomes desert=%d savanna_family=%d badlands_family=%d",
            desert, savanna, badlands
        ));
        send(source,
            desert == 0
                ? "STATUS=FAIL desert is unreachable in this audit window"
                : "STATUS=PASS desert is reachable in this audit window"
        );
        return desert > 0 ? 1 : 0;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
