package dev.nexusmc.landscape.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Samples height changes around an operator and reports how much terrain is
 * traversable by normal jeeps and more capable all-terrain vehicles.
 */
public final class RouteAuditCommand {
    private static final int SAMPLE_STEP = 4;

    private RouteAuditCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("nexuslandscape")
            .requires(source -> source.hasPermission(2))
            .then(literal("route_audit")
                .executes(context -> audit(context.getSource(), 64))
                .then(argument("radius", IntegerArgumentType.integer(16, 128))
                    .executes(context -> audit(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "radius")
                    )))));
    }

    private static int audit(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        int centerX = Mth.floor(source.getPosition().x);
        int centerZ = Mth.floor(source.getPosition().z);
        int jeepEdges = 0;
        int allTerrainEdges = 0;
        int cliffEdges = 0;
        int totalEdges = 0;
        long totalRise = 0;

        for (int x = centerX - radius; x <= centerX + radius; x += SAMPLE_STEP) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += SAMPLE_STEP) {
                int height = height(level, x, z);
                if (x + SAMPLE_STEP <= centerX + radius) {
                    int rise = Math.abs(height - height(level, x + SAMPLE_STEP, z));
                    totalRise += rise;
                    totalEdges++;
                    jeepEdges += rise <= 2 ? 1 : 0;
                    allTerrainEdges += rise <= 4 ? 1 : 0;
                    cliffEdges += rise >= 8 ? 1 : 0;
                }
                if (z + SAMPLE_STEP <= centerZ + radius) {
                    int rise = Math.abs(height - height(level, x, z + SAMPLE_STEP));
                    totalRise += rise;
                    totalEdges++;
                    jeepEdges += rise <= 2 ? 1 : 0;
                    allTerrainEdges += rise <= 4 ? 1 : 0;
                    cliffEdges += rise >= 8 ? 1 : 0;
                }
            }
        }

        if (totalEdges == 0) {
            source.sendFailure(Component.literal("Nexus route audit: недостаточно данных."));
            return 0;
        }

        double jeepPercent = jeepEdges * 100.0 / totalEdges;
        double allTerrainPercent = allTerrainEdges * 100.0 / totalEdges;
        double cliffPercent = cliffEdges * 100.0 / totalEdges;
        double averageRise = totalRise / (double) totalEdges;
        source.sendSuccess(() -> Component.literal(String.format(
            "Nexus route audit r=%d: джип %.1f%%, вездеход %.1f%%, обрывы %.1f%%, средний перепад %.2f блока/4м",
            radius,
            jeepPercent,
            allTerrainPercent,
            cliffPercent,
            averageRise
        )), false);
        return Mth.floor(allTerrainPercent);
    }

    private static int height(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }
}
