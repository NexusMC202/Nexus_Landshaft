package dev.nexusmc.landscape.worldgen.v2.field;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Diagnostic and placement view of the same regional math used by
 * {@link RegionalFieldDensityFunction}.
 */
public final class NexusV2FieldSampler {
    private final RandomState randomState;
    private final NormalNoise temperature;
    private final NormalNoise humidity;
    private final NormalNoise warpX;
    private final NormalNoise warpZ;
    private final NormalNoise macro;
    private final NormalNoise detail;
    private final NormalNoise ridge;
    private final NormalNoise volcanic;
    private final NormalNoise composition;

    public NexusV2FieldSampler(RandomState randomState) {
        this.randomState = randomState;
        this.temperature = randomState.getOrCreateNoise(NexusV2Noises.CLIMATE_TEMPERATURE);
        this.humidity = randomState.getOrCreateNoise(NexusV2Noises.CLIMATE_HUMIDITY);
        this.warpX = randomState.getOrCreateNoise(NexusV2Noises.WARP_X);
        this.warpZ = randomState.getOrCreateNoise(NexusV2Noises.WARP_Z);
        this.macro = randomState.getOrCreateNoise(NexusV2Noises.PROVINCE_MACRO);
        this.detail = randomState.getOrCreateNoise(NexusV2Noises.PROVINCE_DETAIL);
        this.ridge = randomState.getOrCreateNoise(NexusV2Noises.PROVINCE_RIDGE);
        this.volcanic = randomState.getOrCreateNoise(NexusV2Noises.VOLCANIC_ARC);
        this.composition = randomState.getOrCreateNoise(NexusV2Noises.COMPOSITION);
    }

    public RegionalFieldMath.Sample sample(int blockX, int blockZ) {
        double warpSampleX = warpX.getValue(
            blockX * RegionalFieldMath.WARP_SCALE,
            0.0,
            blockZ * RegionalFieldMath.WARP_SCALE
        );
        double warpSampleZ = warpZ.getValue(
            blockX * RegionalFieldMath.WARP_SCALE,
            0.0,
            blockZ * RegionalFieldMath.WARP_SCALE
        );
        double warpedX = blockX + warpSampleX * RegionalFieldMath.WARP_BLOCKS;
        double warpedZ = blockZ + warpSampleZ * RegionalFieldMath.WARP_BLOCKS;
        DensityFunction.FunctionContext context =
            new DensityFunction.SinglePointContext(blockX, 64, blockZ);

        return RegionalFieldMath.sample(
            randomState.router().continents().compute(context),
            temperature.getValue(
                warpedX * RegionalFieldMath.CLIMATE_SCALE,
                0.0,
                warpedZ * RegionalFieldMath.CLIMATE_SCALE
            ),
            humidity.getValue(
                warpedX * RegionalFieldMath.CLIMATE_SCALE,
                0.0,
                warpedZ * RegionalFieldMath.CLIMATE_SCALE
            ),
            macro.getValue(
                warpedX * RegionalFieldMath.MACRO_SCALE,
                0.0,
                warpedZ * RegionalFieldMath.MACRO_SCALE
            ),
            detail.getValue(
                warpedX * RegionalFieldMath.DETAIL_SCALE,
                0.0,
                warpedZ * RegionalFieldMath.DETAIL_SCALE
            ),
            ridge.getValue(
                warpedX * RegionalFieldMath.RIDGE_SCALE,
                0.0,
                warpedZ * RegionalFieldMath.RIDGE_SCALE
            ),
            volcanic.getValue(
                warpedX * RegionalFieldMath.VOLCANIC_SCALE,
                0.0,
                warpedZ * RegionalFieldMath.VOLCANIC_SCALE
            ),
            composition.getValue(
                warpedX * RegionalFieldMath.COMPOSITION_SCALE,
                0.0,
                warpedZ * RegionalFieldMath.COMPOSITION_SCALE
            )
        );
    }
}
