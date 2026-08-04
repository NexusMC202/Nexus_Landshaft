package dev.nexusmc.landscape.worldgen.v2.field;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Verifies that the configured climate router can still reach the hot, cold,
 * wet and dry bands required by the vanilla overworld multi-noise biome table.
 */
public final class ClimateRouterRangeSelfTest {
    private static final String ROOT =
        "/data/nexus_landscape/worldgen/density_function/climate/";

    private ClimateRouterRangeSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Interval temperature = read(ROOT + "temperature.json");
        Interval humidity = read(ROOT + "humidity.json");

        require(temperature.min() <= -0.55,
            "temperature no longer reaches cold climates: " + temperature);
        require(temperature.max() >= 0.75,
            "temperature no longer reaches hot climates: " + temperature);
        require(humidity.min() <= -0.55,
            "humidity no longer reaches dry climates: " + humidity);
        require(humidity.max() >= 0.55,
            "humidity no longer reaches wet climates: " + humidity);

        System.out.println("ClimateRouterRangeSelfTest passed: temperature="
            + temperature + " humidity=" + humidity);
    }

    private static Interval read(String path) throws IOException {
        try (Reader reader = new InputStreamReader(
            requireResource(path), StandardCharsets.UTF_8)) {
            return interval(JsonParser.parseReader(reader));
        }
    }

    private static java.io.InputStream requireResource(String path) {
        java.io.InputStream stream =
            ClimateRouterRangeSelfTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing resource: " + path);
        }
        return stream;
    }

    private static Interval interval(JsonElement element) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                double value = element.getAsDouble();
                return new Interval(value, value);
            }
            return new Interval(-1.0, 1.0);
        }

        JsonObject object = element.getAsJsonObject();
        String type = object.get("type").getAsString();
        return switch (type) {
            case "minecraft:add" -> interval(object.get("argument1"))
                .add(interval(object.get("argument2")));
            case "minecraft:mul" -> interval(object.get("argument1"))
                .multiply(interval(object.get("argument2")));
            case "minecraft:abs" -> interval(object.get("argument")).abs();
            case "minecraft:clamp" -> interval(object.get("input")).clamp(
                object.get("min").getAsDouble(),
                object.get("max").getAsDouble()
            );
            case "minecraft:y_clamped_gradient" -> new Interval(
                Math.min(object.get("from_value").getAsDouble(),
                    object.get("to_value").getAsDouble()),
                Math.max(object.get("from_value").getAsDouble(),
                    object.get("to_value").getAsDouble())
            );
            case "minecraft:shifted_noise" -> new Interval(-1.0, 1.0);
            default -> throw new IllegalArgumentException(
                "Unsupported density function in climate test: " + type
            );
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Interval(double min, double max) {
        private Interval add(Interval other) {
            return new Interval(min + other.min, max + other.max);
        }

        private Interval multiply(Interval other) {
            double a = min * other.min;
            double b = min * other.max;
            double c = max * other.min;
            double d = max * other.max;
            return new Interval(
                Math.min(Math.min(a, b), Math.min(c, d)),
                Math.max(Math.max(a, b), Math.max(c, d))
            );
        }

        private Interval abs() {
            if (min >= 0.0) {
                return this;
            }
            if (max <= 0.0) {
                return new Interval(-max, -min);
            }
            return new Interval(0.0, Math.max(-min, max));
        }

        private Interval clamp(double lower, double upper) {
            return new Interval(
                Math.max(lower, Math.min(upper, min)),
                Math.max(lower, Math.min(upper, max))
            );
        }
    }
}
