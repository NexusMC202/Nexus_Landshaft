package dev.nexusmc.landscape.diagnostics;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Regression checks for the logarithmic histogram used by Stage6Profiler.
 */
public final class Stage6ProfilerSelfTest {
    private Stage6ProfilerSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Class<?> measurementClass = Class.forName(
            "dev.nexusmc.landscape.diagnostics.Stage6Profiler$Measurement"
        );
        Constructor<?> constructor = measurementClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object measurement = constructor.newInstance();

        Method record = measurementClass.getDeclaredMethod("record", long.class);
        Method snapshot = measurementClass.getDeclaredMethod("snapshot", boolean.class);
        record.setAccessible(true);
        snapshot.setAccessible(true);

        long[] samples = {
            1_000_000L,
            2_000_000L,
            4_000_000L,
            8_000_000L,
            16_000_000L,
            32_000_000L,
            64_000_000L,
            128_000_000L,
            256_000_000L,
            512_000_000L
        };
        for (long sample : samples) {
            record.invoke(measurement, sample);
        }

        Object first = snapshot.invoke(measurement, false);
        Class<?> snapshotClass = first.getClass();
        Method calls = snapshotClass.getDeclaredMethod("calls");
        Method total = snapshotClass.getDeclaredMethod("totalNanos");
        Method average = snapshotClass.getDeclaredMethod("averageNanos");
        Method p50 = snapshotClass.getDeclaredMethod("p50Nanos");
        Method p95 = snapshotClass.getDeclaredMethod("p95Nanos");
        Method maximum = snapshotClass.getDeclaredMethod("maximumNanos");
        calls.setAccessible(true);
        total.setAccessible(true);
        average.setAccessible(true);
        p50.setAccessible(true);
        p95.setAccessible(true);
        maximum.setAccessible(true);

        require((long)calls.invoke(first) == samples.length, "call count mismatch");
        require((long)total.invoke(first) == sum(samples), "total mismatch");
        require(
            (long)average.invoke(first) == sum(samples) / samples.length,
            "average mismatch"
        );
        require(
            (long)p50.invoke(first) >= 8_388_608L
                && (long)p50.invoke(first) <= 16_000_000L,
            "p50 bucket is outside the expected logarithmic range"
        );
        require(
            (long)p95.invoke(first) >= 268_435_456L
                && (long)p95.invoke(first) <= 512_000_000L,
            "p95 bucket is outside the expected logarithmic range"
        );
        require((long)maximum.invoke(first) == 512_000_000L, "maximum mismatch");

        Object reset = snapshot.invoke(measurement, true);
        require((long)calls.invoke(reset) == samples.length, "reset snapshot lost calls");
        Object empty = snapshot.invoke(measurement, false);
        require((long)calls.invoke(empty) == 0L, "reset did not clear calls");
        require((long)total.invoke(empty) == 0L, "reset did not clear total");
        require((long)p50.invoke(empty) == 0L, "empty p50 must be zero");
        require((long)p95.invoke(empty) == 0L, "empty p95 must be zero");
        require((long)maximum.invoke(empty) == 0L, "empty maximum must be zero");

        System.out.println("Stage6ProfilerSelfTest: PASS");
    }

    private static long sum(long[] values) {
        long result = 0L;
        for (long value : values) {
            result += value;
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
