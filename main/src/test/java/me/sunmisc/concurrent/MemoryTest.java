package me.sunmisc.concurrent;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sunmisc.utils.concurrent.memory.SegmentsMemory;
import sunmisc.utils.concurrent.memory.ModifiableMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MemoryTest {

    @Test
    public void modifyMemory() {
        final int size = 1 << 14;
        final List<Integer> list = new ArrayList<>();
        final ModifiableMemory<Integer> memory = new SegmentsMemory<>(size);
        for (int index = 0; index < size; ++index) {
            final int value = ThreadLocalRandom.current().nextInt();
            list.add(index, value);
            memory.store(index, value);
        }
        for (int index = 0; index < size; ++index) {
            final int value = memory.fetch(index);
            final int expected = list.get(index);
            MatcherAssert.assertThat(
                    String.format(
                            "The value of %s at index %s does not match the original: %s",
                            value, index, expected
                    ),
                    value,
                    CoreMatchers.equalTo(expected)
            );
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 32})
    public void reallocMemory(final int capacity) {
        final int size = 1 << 14;
        final ConcurrentMap<Integer, Integer> map = new ConcurrentHashMap<>();
        final Queue<Integer> indexes = new ConcurrentLinkedQueue<>();
        final AtomicInteger counted = new AtomicInteger(0);
        final AtomicReference<ModifiableMemory<Integer>> memory =
                new AtomicReference<>(
                        new SegmentsMemory<>(capacity)
                );
        try (final ExecutorService executor = Executors.newWorkStealingPool()) {
            for (int a = 0; a < size; ++a) {
                executor.execute(() -> {
                    final int inc = counted.getAndIncrement();
                    if (memory.get().length() - 1 <= inc) {
                        memory.updateAndGet(
                                old -> old.length() - 1 <= inc
                                        ? old.realloc(Math.max(old.length(), inc + 1))
                                        : old
                        );
                    }
                    indexes.add(inc);
                    map.put(inc, inc);
                    memory.get().store(inc, inc);
                });
            }
        }
        indexes.forEach(index -> {
            final int value = memory.get().fetch(index);
            final int expected = map.get(index);
            MatcherAssert.assertThat(
                    String.format(
                            "The value of %s at index %s does not match the original: %s",
                            value, index, expected
                    ),
                    value,
                    CoreMatchers.equalTo(expected)
            );
        });
    }
}
