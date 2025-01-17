package me.sunmisc.concurrent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sunmisc.utils.concurrent.sets.ConcurrentBitSet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class ConcurrentBitSetTest {
    private ConcurrentBitSet bits;
    private Map<Integer, Boolean> hash;

    @BeforeEach
    public void setup() {
        this.bits = new ConcurrentBitSet();
        this.hash = new ConcurrentHashMap<>();
        for (int i = 0; i < 16; ++i) {
            this.bits.add(i);
            this.hash.put(i, true);
        }
    }

    @Test
    public void insertBit() {
        final int size = 1 << 14;
        try (final ExecutorService executor = Executors.newWorkStealingPool()) {
            for (int a = 0; a < size; ++a) {
                executor.execute(() -> {
                    final int delta = ThreadLocalRandom.current().nextInt(0, size);
                    this.bits.add(delta);
                    this.hash.put(delta, true);
                });
            }
        }
        Assertions.assertEquals(
                this.bits,
                this.hash.keySet(),
                "Collections should match"
        );
    }

    @Test
    public void deleteBit() {
        try (final ExecutorService executor = Executors.newWorkStealingPool()) {
            executor.execute(() -> {
                for (final int index : this.bits) {
                    this.bits.remove(index);
                    this.hash.remove(index);
                }
            });
        }
        Assertions.assertEquals(
                this.bits.size(),
                this.hash.size(),
                "Collections should match"
        );
    }
}
