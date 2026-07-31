package dev.nexusmc.landscape.worldgen.v2.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record AnalyticalTerrainDensityFunction(
    Channel channel,
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
    private static final Codec<Channel> CHANNEL_CODEC = Codec.STRING.xmap(
        Channel::fromSerializedName,
        Channel::serializedName
    );
    public static final MapCodec<AnalyticalTerrainDensityFunction> MAP_CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            CHANNEL_CODEC.fieldOf("channel").forGetter(AnalyticalTerrainDensityFunction::channel),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continentalness")
                .forGetter(AnalyticalTerrainDensityFunction::continentalness),
            DensityFunction.NoiseHolder.CODEC.fieldOf("temperature")
                .forGetter(AnalyticalTerrainDensityFunction::temperature),
            DensityFunction.NoiseHolder.CODEC.fieldOf("humidity")
                .forGetter(AnalyticalTerrainDensityFunction::humidity),
            DensityFunction.NoiseHolder.CODEC.fieldOf("warp_x")
                .forGetter(AnalyticalTerrainDensityFunction::warpX),
            DensityFunction.NoiseHolder.CODEC.fieldOf("warp_z")
                .forGetter(AnalyticalTerrainDensityFunction::warpZ),
            DensityFunction.NoiseHolder.CODEC.fieldOf("macro")
                .forGetter(AnalyticalTerrainDensityFunction::macro),
            DensityFunction.NoiseHolder.CODEC.fieldOf("detail")
                .forGetter(AnalyticalTerrainDensityFunction::detail),
            DensityFunction.NoiseHolder.CODEC.fieldOf("ridge")
                .forGetter(AnalyticalTerrainDensityFunction::ridge),
            DensityFunction.NoiseHolder.CODEC.fieldOf("volcanic")
                .forGetter(AnalyticalTerrainDensityFunction::volcanic),
            DensityFunction.NoiseHolder.CODEC.fieldOf("composition")
                .forGetter(AnalyticalTerrainDensityFunction::composition)
        ).apply(instance, AnalyticalTerrainDensityFunction::new));
    private static final KeyDispatchDataCodec<AnalyticalTerrainDensityFunction> CODEC =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        double warpSampleX = warpX.getValue(
            context.blockX() * dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath.WARP_SCALE,
            0.0,
            context.blockZ() * dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath.WARP_SCALE
        );
        double warpSampleZ = warpZ.getValue(
            context.blockX() * dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath.WARP_SCALE,
            0.0,
            context.blockZ() * dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath.WARP_SCALE
        );
        double x = context.blockX()
            + warpSampleX * dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath.WARP_BLOCKS;
        double z = context.blockZ()
            + warpSampleZ * dev.nexusmc.landscape.worldgen.v2.field.RegionalFieldMath.WARP_BLOCKS;
        var sample = AnalyticalTerrainMath.sample(
            continentalness.compute(context),
            temperature.getValue(x / 2_560.0, 0.0, z / 2_560.0),
            humidity.getValue(x / 2_560.0, 0.0, z / 2_560.0),
            macro.getValue(x / 3_072.0, 0.0, z / 3_072.0),
            detail.getValue(x / 1_536.0, 0.0, z / 1_536.0),
            ridge.getValue(x / 2_048.0, 0.0, z / 2_048.0),
            volcanic.getValue(x / 5_120.0, 0.0, z / 5_120.0),
            composition.getValue(x / 4_096.0, 0.0, z / 4_096.0)
        );
        return switch (channel) {
            case DENSITY -> AnalyticalTerrainMath.density(sample.surfaceY(), context.blockY());
            case SURFACE_Y -> sample.surfaceY();
            case BROAD_VALLEY -> sample.broadValleyY();
            case CANYON_INCISION -> sample.canyonIncisionY();
            case GLACIER_CARVE -> sample.glacierCarveY();
            case REGIONAL_EROSION -> sample.regionalErosionY();
        };
    }

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new AnalyticalTerrainDensityFunction(
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
        return switch (channel) {
            case DENSITY -> -1.5;
            case SURFACE_Y -> -48.0;
            case REGIONAL_EROSION -> -10.0;
            default -> 0.0;
        };
    }

    @Override
    public double maxValue() {
        return switch (channel) {
            case DENSITY -> 1.5;
            case SURFACE_Y -> 304.0;
            case BROAD_VALLEY -> 24.0;
            case CANYON_INCISION -> 34.0;
            case GLACIER_CARVE -> 22.0;
            case REGIONAL_EROSION -> 10.0;
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    public enum Channel {
        DENSITY,
        SURFACE_Y,
        BROAD_VALLEY,
        CANYON_INCISION,
        GLACIER_CARVE,
        REGIONAL_EROSION;

        String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Channel fromSerializedName(String name) {
            for (Channel channel : values()) {
                if (channel.serializedName().equals(name)) {
                    return channel;
                }
            }
            throw new IllegalArgumentException("Unknown analytical terrain channel: " + name);
        }
    }
}
