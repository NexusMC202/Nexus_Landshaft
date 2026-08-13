package dev.nexusmc.landscape.command;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import dev.nexusmc.landscape.worldgen.v2.tree.TreeMaterialResolver;
import dev.nexusmc.landscape.worldgen.v2.tree.TreeModelCache;
import dev.nexusmc.landscape.worldgen.v2.tree.TreePlacementProbeTelemetry;
import dev.nexusmc.landscape.worldgen.v2.tree.TreeRuntimeTelemetry;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Operator diagnostics for procedural tree generation. */
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
        TreePlacementProbeTelemetry.Snapshot probes =
            TreePlacementProbeTelemetry.snapshot();
        TreeModelCache.Snapshot cache = TreeModelCache.snapshot();
        TreeModelCache.StructureSnapshot structure =
            TreeModelCache.structureSnapshot();
        TreeModelCache.CrownSnapshot crown = TreeModelCache.crownSnapshot();
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
            "cache_size=%d cache_capacity=%d cache_requests=%d "
                + "material_cache_size=%d",
            cache.size(),
            cache.capacity(),
            cache.requests(),
            TreeMaterialResolver.cacheSize()
        ));
        send(source, String.format(
            Locale.ROOT,
            "envelope_checks=%d envelope_probes=%d avg_envelope_probes=%.2f "
                + "final_checks=%d final_probes=%d avg_final_probes=%.2f",
            probes.envelopeChecks(),
            probes.envelopeProbes(),
            probes.averageEnvelopeProbes(),
            probes.finalChecks(),
            probes.finalProbes(),
            probes.averageFinalProbes()
        ));
        send(source, String.format(
            Locale.ROOT,
            "probe_checks=%d probe_total=%d",
            probes.totalChecks(),
            probes.totalProbes()
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
        send(source, String.format(
            Locale.ROOT,
            "crown_leaves=%d leeward=%d windward=%d neutral=%d "
                + "leeward_share=%.3f balance=%.3f",
            crown.leafCount(),
            crown.leewardLeaves(),
            crown.windwardLeaves(),
            crown.neutralLeaves(),
            crown.leewardShare(),
            crown.directionalBalance()
        ));
        send(source, String.format(
            Locale.ROOT,
            "crown_center_x=%.3f crown_center_z=%.3f wind_projection=%.3f",
            crown.centerX(),
            crown.centerZ(),
            crown.windProjection()
        ));
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        TreeRuntimeTelemetry.reset();
        TreePlacementProbeTelemetry.reset();
        TreeModelCache.clear();
        TreeMaterialResolver.clearCache();
        source.sendSuccess(() -> Component.literal(
            "Nexus procedural tree counters and caches reset."
        ), false);
        return 1;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
