package dev.nexusmc.landscape.worldgen.v2.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Regression coverage for the strict-capacity cache contract, including
 * concurrent writers racing with clear operations.
 */
public final class BoundedConcurrentCacheSelfTest {
    private BoundedConcurrentCacheSelfTest() {
    }

    public static void main(String[] args) throws InterruptedException {
        rejectsInvalidCapacity();
        preservesStrictCapacity();
        clearResetsAdmissionState();
        concurrentClearAndPutStayBounded();
        System.out.println("BoundedConcurrentCacheSelfTest: PASS");
    }

    private static void rejectsInvalidCapacity() {
        try {
            new BoundedConcurrentCache<String, String>(0);
            throw new AssertionError("zero capacity was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void preservesStrictCapacity() {
        BoundedConcurrentCache<Integer, Integer> cache =
            new BoundedConcurrentCache<>(4);
        for (int key = 0; key < 64; key++) {
            cache.put(key, key);
        }
        check(cache.size() == 4, "cache exceeded or missed its capacity");
    }

    private static void clearResetsAdmissionState() {
        BoundedConcurrentCache<Integer, Integer> cache =
            new BoundedConcurrentCache<>(2);
        cache.put(1, 1);
        cache.put(2, 2);
        cache.clear();
        check(cache.size() == 0, "clear did not reset size");
        check(cache.get(1) == null && cache.get(2) == null,
            "clear left stale values");
        cache.put(3, 3);
        cache.put(4, 4);
        check(cache.size() == 2, "cache did not admit values after clear");
    }

    private static void concurrentClearAndPutStayBounded()
        throws InterruptedException {
        int capacity = 32;
        BoundedConcurrentCache<Integer, Integer> cache =
            new BoundedConcurrentCache<>(capacity);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();

        for (int worker = 0; worker < 6; worker++) {
            int offset = worker * 100_000;
            Thread thread = new Thread(() -> {
                await(start);
                for (int index = 0; index < 20_000; index++) {
                    cache.put(offset + index, index);
                    int size = cache.size();
                    check(size >= 0 && size <= capacity,
                        "concurrent size escaped capacity: " + size);
                }
            }, "cache-writer-" + worker);
            workers.add(thread);
        }

        Thread clearer = new Thread(() -> {
            await(start);
            for (int index = 0; index < 4_000; index++) {
                cache.clear();
                int size = cache.size();
                check(size >= 0 && size <= capacity,
                    "clear exposed invalid size: " + size);
            }
        }, "cache-clearer");
        workers.add(clearer);

        workers.forEach(Thread::start);
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        check(cache.size() <= capacity, "final cache size exceeded capacity");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
