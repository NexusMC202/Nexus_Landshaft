package dev.nexusmc.landscape.worldgen.v2.tree;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Resolves validated pure tree material ids against the Minecraft block registry. */
public final class TreeMaterialResolver {
    private static final ConcurrentMap<TreeMaterialProfile, ResolvedMaterials> CACHE =
        new ConcurrentHashMap<>();

    private TreeMaterialResolver() {
    }

    public static ResolvedMaterials resolve(TreeMaterialProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("tree material profile is required");
        }
        return CACHE.computeIfAbsent(profile, TreeMaterialResolver::resolveUncached);
    }

    private static ResolvedMaterials resolveUncached(TreeMaterialProfile profile) {
        return new ResolvedMaterials(
            resolveBlock(profile.logBlockId()),
            resolveBlock(profile.leavesBlockId())
        );
    }

    static Block resolveBlock(String id) {
        ResourceLocation location;
        try {
            location = ResourceLocation.parse(id);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                "invalid tree material block id: " + id,
                exception
            );
        }
        return BuiltInRegistries.BLOCK.getOptional(location)
            .orElseThrow(() -> new IllegalArgumentException(
                "unknown tree material block: " + id
            ));
    }

    public static int cacheSize() {
        return CACHE.size();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public record ResolvedMaterials(Block logBlock, Block leavesBlock) {
        public ResolvedMaterials {
            if (logBlock == null || leavesBlock == null) {
                throw new IllegalArgumentException("resolved tree materials are required");
            }
            if (logBlock == leavesBlock) {
                throw new IllegalArgumentException("resolved log and leaves blocks must differ");
            }
        }
    }
}
