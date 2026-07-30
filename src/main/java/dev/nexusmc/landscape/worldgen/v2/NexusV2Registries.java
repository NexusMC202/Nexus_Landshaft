package dev.nexusmc.landscape.worldgen.v2;

import com.mojang.serialization.MapCodec;
import dev.nexusmc.landscape.NexusLandscape;
import dev.nexusmc.landscape.worldgen.v2.chunk.NexusV2ChunkGenerator;
import dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldDensityFunction;
import dev.nexusmc.landscape.worldgen.v2.hydrology.HydrologyFieldDensityFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NexusV2Registries {
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>>
        CHUNK_GENERATORS = DeferredRegister.create(
            Registries.CHUNK_GENERATOR,
            NexusLandscape.MOD_ID
        );
    private static final DeferredRegister<MapCodec<? extends DensityFunction>>
        DENSITY_FUNCTION_TYPES = DeferredRegister.create(
            Registries.DENSITY_FUNCTION_TYPE,
            NexusLandscape.MOD_ID
        );

    static {
        CHUNK_GENERATORS.register(
            "nexus_v2",
            () -> NexusV2ChunkGenerator.CODEC
        );
        DENSITY_FUNCTION_TYPES.register(
            "regional_field",
            () -> RegionalFieldDensityFunction.MAP_CODEC
        );
        DENSITY_FUNCTION_TYPES.register(
            "hydrology_field",
            () -> HydrologyFieldDensityFunction.MAP_CODEC
        );
    }

    private NexusV2Registries() {
    }

    public static void register(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
        DENSITY_FUNCTION_TYPES.register(modBus);
    }
}
