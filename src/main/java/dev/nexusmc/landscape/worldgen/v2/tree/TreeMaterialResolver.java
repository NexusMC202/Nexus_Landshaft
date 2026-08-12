package dev.nexusmc.landscape.worldgen.v2.tree;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Resolves validated pure tree material ids against the Minecraft block registry. */
public final class TreeMaterialResolver {
    private TreeMaterialResolver() {
    }

    public static ResolvedMaterials resolve(TreeMaterialProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("tree material profile is required");
        }
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
            throw new IllegalArgumentException("invalid tree material block id: " + id, exception);
        }
        return BuiltInRegistries.BLOCK.getOptional(location)
            .orElseThrow(() -> new IllegalArgumentException(
                "unknown tree material block: " + id
            ));
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
