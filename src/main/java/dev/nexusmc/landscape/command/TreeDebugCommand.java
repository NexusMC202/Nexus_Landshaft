package dev.nexusmc.landscape.command;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import dev.nexusmc.landscape.worldgen.v2.tree.TreeModelCache;
import dev.nexusmc.landscape.worldgen.v2.tree.TreeRuntimeTelemetry;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Read-only operator diagnostics for procedural tree generation. */
public final class TreeDebugCommand {
    private TreeDebugCommand() {
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(literal("nexustree")
            .requires(source -> source.hasPermission(
                Stage6CommandPolicy.REQUIRED_PERMISSION_LEVEL
            ))
            .then(literal("counters")
                .executes(context -> counters(context.getSource())))
            .then(literal("reset")
                .executes(context -> reset(context.getSource()))));
    }

    private static int counters(CommandSourceStack source) {
        TreeRuntimeTelemetry.Snapshot runtime = TreeRuntimeTelemetry.snapshot();
        TreeModelCache.Snapshot cache = TreeModelCache.snapshot();
        TreeModelCache.StructureSnapshot structure =
            TreeModelCache.structureSnapshot();
        send(source, "Nexus procedural tree counters");
        send(source, String.format(
            Locale.ROOT,
            "attempts=%d placed=%d collisions=%d quota_rejected=%d "
                + "disabled=%d unsupported=%d",
            runtime.attempts(),
            runtime.placed(),
            runtime.collisions(),
            runtime.quotaRejected(),
            runtime.disabled(),
            runtime.unsupported()
        ));
        send(source, String.format(
            Locale.ROOT,
            "cache_hits=%d cache_misses=%d cache_lookup_skipped=%d "
                + "hit_rate=%.3f",
            runtime.cacheHits(),
            runtime.cacheMisses(),
            runtime.cacheLookupSkipped(),
            runtime.cacheHitRate()
        ));
        send(source, String.format(
            Locale.ROOT,
            "cache_size=%d cache_capacity=%d cache_requests=%d",
            cache.size(),
            cache.capacity(),
            cache.requests()
        ));
        send(source, String.format(
            Locale.ROOT,
            "generated_segments=%d trunk=%d roots=%d live_branches=%d "
                + "dead_branches=%d secondary_leaders=%d",
            structure.totalSegments(),
            structure.trunkSegments(),
            structure.rootSegments(),
            structure.liveBranchSegments(),
            structure.deadBranchSegments(),
            structure.secondaryLeaderSegments()
        ));
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        TreeRuntimeTelemetry.reset();
        TreeModelCache.clear();
        source.sendSuccess(() -> Component.literal(
            "Nexus procedural tree counters and model cache reset."
        ), false);
        return 1;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
