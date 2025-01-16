package me.sunmisc.concurrent;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sunmisc.utils.concurrent.lists.ConcurrentArrayList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public final class ConcurrentArrListTest {
    private List<Integer> concurrent;
    private List<Integer> cow;

    @BeforeEach
    public void setup() {
        this.concurrent = new ConcurrentArrayList<>();
        this.cow = new CopyOnWriteArrayList<>();
        this.concurrent.add(0);
        this.cow.add(0);
    }

    @Test
    public void modifyToList() {
        try (final ExecutorService executor = Executors.newWorkStealingPool()) {
            final int add = ThreadLocalRandom.current().nextInt(0, 16);
            final int delete = add - 1;
            for (int a = 0; a < add; ++a) {
                executor.execute(() -> {
                    final int rand = ThreadLocalRandom.current().nextInt();
                    this.cow.add(rand);
                    this.concurrent.add(rand);
                });
            }
            for (int d = 0; d < delete; ++d) {
                executor.execute(() -> {
                    this.cow.removeFirst();
                    this.concurrent.removeFirst();
                });
            }
        }
        MatcherAssert.assertThat(
                "The lists should be equal after concurrent operations",
                this.cow,
                CoreMatchers.equalTo(this.concurrent)
        );
        MatcherAssert.assertThat(
                "The size should match the expected value",
                this.cow.size(),
                CoreMatchers.equalTo(this.concurrent.size())
        );
    }
}
