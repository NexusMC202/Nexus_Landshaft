package dev.nexusmc.landscape.worldgen;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class WorldgenBlocks {
    private static final List<String> RARE_FLOWERS = List.of(
        "natures_spirit:lotus_flower",
        "natures_spirit:purple_wisteria",
        "natures_spirit:bleeding_heart",
        "minecraft:allium",
        "minecraft:blue_orchid"
    );

    private WorldgenBlocks() {
    }

    static BlockState rareFlower(LevelReader level, BlockPos pos, int offset) {
        for (int i = 0; i < RARE_FLOWERS.size(); i++) {
            String id = RARE_FLOWERS.get(Math.floorMod(i + offset, RARE_FLOWERS.size()));
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
            if (block.isPresent() && block.get() != Blocks.AIR) {
                BlockState state = block.get().defaultBlockState();
                if (state.canSurvive(level, pos)) {
                    return state;
                }
            }
        }
        return Blocks.ALLIUM.defaultBlockState();
    }
}
