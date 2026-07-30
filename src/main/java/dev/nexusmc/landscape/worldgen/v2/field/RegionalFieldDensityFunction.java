package dev.nexusmc.landscape.worldgen.v2.field;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record RegionalFieldDensityFunction(
    RegionalFieldMath.Channel channel,
    DensityFunction continentalness,
    DensityFunction.NoiseHolder temperature,
    DensityFunction.NoiseHolder humidity,
    DensityFunction.NoiseHolder warpX,
    DensityFunction.NoiseHolder warpZ,
    DensityFunction.NoiseHolder macro,
    DensityFunction.NoiseHolder detail,
    DensityFunction.NoiseHolder ridge,
    DensityFunction.NoiseHolder volcanic,
    DensityFunction.NoiseHolder composition
) implements DensityFunction {
    private static final Codec<RegionalFieldMath.Channel> CHANNEL_CODEC = Codec.STRING.xmap(
        RegionalFieldMath.Channel::fromSerializedName,
        RegionalFieldMath.Channel::serializedName
    );

    public static final MapCodec<RegionalFieldDensityFunction> MAP_CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            CHANNEL_CODEC.fieldOf("channel").forGetter(RegionalFieldDensityFunction::channel),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continentalness")
                .forGetter(RegionalFieldDensityFunction::continentalness),
            DensityFunction.NoiseHolder.CODEC.fieldOf("temperature")
                .forGetter(RegionalFieldDensityFunction::temperature),
            DensityFunction.NoiseHolder.CODEC.fieldOf("humidity")
                .forGetter(RegionalFieldDensityFunction::humidity),
            DensityFunction.NoiseHolder.CODEC.fieldOf("warp_x")
                .forGetter(RegionalFieldDensityFunction::warpX),
            DensityFunction.NoiseHolder.CODEC.fieldOf("warp_z")
                .forGetter(RegionalFieldDensityFunction::warpZ),
            DensityFunction.NoiseHolder.CODEC.fieldOf("macro")
                .forGetter(RegionalFieldDensityFunction::macro),
            DensityFunction.NoiseHolder.CODEC.fieldOf("detail")
                .forGetter(RegionalFieldDensityFunction::detail),
            DensityFunction.NoiseHolder.CODEC.fieldOf("ridge")
                .forGetter(RegionalFieldDensityFunction::ridge),
            DensityFunction.NoiseHolder.CODEC.fieldOf("volcanic")
                .forGetter(RegionalFieldDensityFunction::volcanic),
            DensityFunction.NoiseHolder.CODEC.fieldOf("composition")
                .forGetter(RegionalFieldDensityFunction::composition)
        ).apply(instance, RegionalFieldDensityFunction::new));

    private static final KeyDispatchDataCodec<RegionalFieldDensityFunction> CODEC =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        int blockX = context.blockX();
        int blockZ = context.blockZ();
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

        return RegionalFieldMath.compute(
            channel,
            continentalness.compute(context),
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

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new RegionalFieldDensityFunction(
            channel,
            continentalness.mapAll(visitor),
            visitor.visitNoise(temperature),
            visitor.visitNoise(humidity),
            visitor.visitNoise(warpX),
            visitor.visitNoise(warpZ),
            visitor.visitNoise(macro),
            visitor.visitNoise(detail),
            visitor.visitNoise(ridge),
            visitor.visitNoise(volcanic),
            visitor.visitNoise(composition)
        ));
    }

    @Override
    public double minValue() {
        return channel == RegionalFieldMath.Channel.MACRO_UPLIFT ? -0.12 : 0.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
