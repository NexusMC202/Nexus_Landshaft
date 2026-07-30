package dev.nexusmc.landscape.worldgen.v2.chunk;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import dev.nexusmc.landscape.worldgen.v2.hydrology.RiverWaterPass;
import dev.nexusmc.landscape.worldgen.v2.surface.SurfaceProvincePass;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Stable V2 generator codec. Stage 4 deliberately delegates every generation
 * stage to vanilla NoiseBasedChunkGenerator. Later V2 passes are added here
 * without changing the generator type stored in level.dat.
 */
public final class NexusV2ChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<NexusV2ChunkGenerator> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source")
                .forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings")
                .forGetter(NexusV2ChunkGenerator::generatorSettings)
        ).apply(instance, instance.stable(NexusV2ChunkGenerator::new)));

    public NexusV2ChunkGenerator(
        BiomeSource biomeSource,
        Holder<NoiseGeneratorSettings> settings
    ) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(
        WorldGenRegion level,
        StructureManager structureManager,
        RandomState random,
        ChunkAccess chunk
    ) {
        super.buildSurface(level, structureManager, random, chunk);
        SurfaceProvincePass.apply(level, chunk, random);
        RiverWaterPass.apply(chunk, random);
    }
}
