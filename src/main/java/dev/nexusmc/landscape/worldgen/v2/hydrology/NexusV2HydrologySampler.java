package dev.nexusmc.landscape.worldgen.v2.hydrology;

import dev.nexusmc.landscape.worldgen.v2.field.NexusV2Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime diagnostic/placement view of the exact noises used by the
 * hydrology density function.
 */
public final class NexusV2HydrologySampler {
    private final dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler terrain;
    private final NormalNoise layout;
    private final NormalNoise tributary;
    private final NormalNoise elevation;
    private final Map<Long, Double> terrainYCache = new HashMap<>();

    public NexusV2HydrologySampler(RandomState randomState) {
        this.terrain =
            new dev.nexusmc.landscape.worldgen.v2.field.NexusV2FieldSampler(randomState);
        this.layout = randomState.getOrCreateNoise(NexusV2Noises.HYDROLOGY_LAYOUT);
        this.tributary = randomState.getOrCreateNoise(NexusV2Noises.HYDROLOGY_TRIBUTARY);
        this.elevation = randomState.getOrCreateNoise(NexusV2Noises.HYDROLOGY_ELEVATION);
    }

    public HydrologyMath.Sample sample(int blockX, int blockZ) {
        return HydrologyMath.sample(blockX, blockZ, noiseSource());
    }

    public HydrologyMath.BasinSample basinSample(int blockX, int blockZ) {
        return HydrologyMath.basinSample(blockX, blockZ, noiseSource());
    }

    private HydrologyMath.NoiseSource noiseSource() {
        return new HydrologyMath.NoiseSource() {
            @Override
            public double layout(double x, double z) {
                return NexusV2HydrologySampler.this.layout.getValue(x, 0.0, z);
            }

            @Override
            public double tributary(double x, double z) {
                return NexusV2HydrologySampler.this.tributary.getValue(x, 0.0, z);
            }

            @Override
            public double elevation(double x, double z) {
                return NexusV2HydrologySampler.this.elevation.getValue(x, 0.0, z);
            }

            @Override
            public double terrainY(double x, double z) {
                int blockX = (int)Math.round(x);
                int blockZ = (int)Math.round(z);
                long key = ((long)blockX << 32) ^ (blockZ & 0xFFFF_FFFFL);
                Double cached = terrainYCache.get(key);
                if (cached != null) {
                    return cached;
                }
                double sampled = terrain.analyticalTerrain(blockX, blockZ).surfaceY();
                terrainYCache.put(key, sampled);
                return sampled;
            }
        };
    }
}
