package dev.nexusmc.landscape.worldgen.v2.hydrology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record HydrologyFieldDensityFunction(
    Channel channel,
    DensityFunction.NoiseHolder layout,
    DensityFunction.NoiseHolder tributary,
    DensityFunction.NoiseHolder elevation
) implements DensityFunction {
    private static final Codec<Channel> CHANNEL_CODEC = Codec.STRING.xmap(
        Channel::fromSerializedName,
        Channel::serializedName
    );
    public static final MapCodec<HydrologyFieldDensityFunction> MAP_CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            CHANNEL_CODEC.fieldOf("channel").forGetter(HydrologyFieldDensityFunction::channel),
            DensityFunction.NoiseHolder.CODEC.fieldOf("layout")
                .forGetter(HydrologyFieldDensityFunction::layout),
            DensityFunction.NoiseHolder.CODEC.fieldOf("tributary")
                .forGetter(HydrologyFieldDensityFunction::tributary),
            DensityFunction.NoiseHolder.CODEC.fieldOf("elevation")
                .forGetter(HydrologyFieldDensityFunction::elevation)
        ).apply(instance, HydrologyFieldDensityFunction::new));
    private static final KeyDispatchDataCodec<HydrologyFieldDensityFunction> CODEC =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        HydrologyMath.Sample sample = HydrologyMath.sample(
            context.blockX(),
            context.blockZ(),
            new HydrologyMath.NoiseSource() {
                @Override
                public double layout(double x, double z) {
                    return HydrologyFieldDensityFunction.this.layout.getValue(x, 0.0, z);
                }

                @Override
                public double tributary(double x, double z) {
                    return HydrologyFieldDensityFunction.this.tributary.getValue(x, 0.0, z);
                }

                @Override
                public double elevation(double x, double z) {
                    return HydrologyFieldDensityFunction.this.elevation.getValue(x, 0.0, z);
                }
            }
        );
        return switch (channel) {
            case MASK -> sample.mask();
            case DISTANCE -> Math.min(sample.distance(), HydrologyMath.BASIN_SIZE);
            case ORDER -> sample.order();
            case WATER_LEVEL -> sample.waterLevel();
            case CARVE -> {
                double relativeY = context.blockY() - sample.waterLevel();
                double vertical = relativeY < -14.0
                    ? 0.0
                    : 1.0 - smoothstep(34.0, 82.0, relativeY);
                yield sample.mask() * vertical;
            }
        };
    }

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new HydrologyFieldDensityFunction(
            channel,
            visitor.visitNoise(layout),
            visitor.visitNoise(tributary),
            visitor.visitNoise(elevation)
        ));
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public double maxValue() {
        return switch (channel) {
            case MASK, CARVE -> 1.0;
            case DISTANCE -> HydrologyMath.BASIN_SIZE;
            case ORDER -> 3.0;
            case WATER_LEVEL -> 96.0;
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    public enum Channel {
        MASK,
        DISTANCE,
        ORDER,
        WATER_LEVEL,
        CARVE;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Channel fromSerializedName(String name) {
            for (Channel value : values()) {
                if (value.serializedName().equals(name)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown Nexus hydrology channel: " + name);
        }
    }
}
