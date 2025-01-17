package me.sunmisc.concurrent;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sunmisc.utils.Scalar;
import sunmisc.utils.concurrent.lazy.ConcurrentLazy;
import sunmisc.utils.concurrent.lazy.RefreshLazy;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScalarTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 32, 128})
    public void doLazyScalarThreadSafe(final int attempts) {
        final List<Scalar<Integer, RuntimeException>> lazy = new LinkedList<>();
        for (int i = 0; i < attempts; ++i) {
            lazy.add(new ConcurrentLazy<>(new CountScalar()));
        }
        try (final ExecutorService executor = Executors.newWorkStealingPool()) {
            lazy.forEach(x -> {
                for (int attempt = 0; attempt < attempts; ++attempt) {
                    executor.execute(x::value);
                }
            });
        }
        MatcherAssert.assertThat(
                "The values must be equal to 0",
                lazy.stream().map(Scalar::value).toList(),
                CoreMatchers.everyItem(CoreMatchers.equalTo(0))
        );
    }

    @Test
    public void doRefreshedScalar() throws InterruptedException {
        final Duration duration = Duration.ofMillis(100);
        final int attempts = 10;
        final Scalar<Integer, RuntimeException> refreshed = new RefreshLazy<>(
                new CountScalar(),
                duration
        );
        for (int at = 0; at < attempts; ++at) {
            MatcherAssert.assertThat(
                    String.format(
                            "Value at attempt %s must match expected value",
                            at
                    ),
                    refreshed.value(),
                    CoreMatchers.equalTo(at)
            );
            Thread.sleep(duration.toMillis());
        }
    }

    private static final class CountScalar implements Scalar<Integer, RuntimeException> {

        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public Integer value() throws RuntimeException {
            return this.count.getAndIncrement();
        }
    }
}
