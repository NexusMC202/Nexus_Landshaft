package dev.nexusmc.landscape.worldgen.v2.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A strict-capacity memoization map. When full it simply stops admitting new
 * entries; cached and uncached computations must therefore remain identical.
 */
public final class BoundedConcurrentCache<K, V> {
    private final ConcurrentHashMap<K, V> values = new ConcurrentHashMap<>();
    private final AtomicInteger admitted = new AtomicInteger();
    private final int capacity;

    public BoundedConcurrentCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public V get(K key) {
        return values.get(key);
    }

    public void put(K key, V value) {
        if (values.containsKey(key) || !reserve()) {
            return;
        }
        V previous = values.putIfAbsent(key, value);
        if (previous != null) {
            admitted.decrementAndGet();
        }
    }

    public int size() {
        return admitted.get();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        values.clear();
        admitted.set(0);
    }

    private boolean reserve() {
        while (true) {
            int current = admitted.get();
            if (current >= capacity) {
                return false;
            }
            if (admitted.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
