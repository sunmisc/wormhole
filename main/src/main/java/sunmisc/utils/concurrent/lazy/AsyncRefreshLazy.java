package sunmisc.utils.concurrent.lazy;

import sunmisc.utils.Scalar;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class AsyncRefreshLazy<V, E extends Throwable> implements Scalar<V, E> {
    private static final Object EMPTY = new Object();
    private final AtomicLong time = new AtomicLong(0);
    @SuppressWarnings("unchecked")
    private final AtomicReference<V> value = new AtomicReference<>((V) EMPTY);
    private final Lock lock = new ReentrantLock();
    private final Scalar<V, E> origin;
    private final Duration latency;
    private final Executor executor;

    public AsyncRefreshLazy(final Scalar<V, E> origin,
                            final Duration latency) {
        this(origin, latency, Executors.newVirtualThreadPerTaskExecutor());
    }

    public AsyncRefreshLazy(final Scalar<V, E> origin,
                            final Duration latency,
                            final Executor executor) {
        this.origin = origin;
        this.latency = latency;
        this.executor = executor;
    }

    @Override
    public V value() throws E {
        final long to = this.time.get();
        if (to >= System.nanoTime()) {
            return this.value.get();
        }
        this.lock.lock();
        try {
            final long now = System.nanoTime();
            final V old = this.value.get();
            if (old == EMPTY) {
                final V first = this.origin.value();
                this.value.set(first);
                this.time.set(now + this.latency.toNanos());
                return first;
            } else if (this.time.get() < now) {
                this.executor.execute(() -> {
                    try {
                        this.value.set(this.origin.value());
                        this.time.set(now + this.latency.toNanos());
                    } catch (final Throwable ex) {
                        throw new RuntimeException(ex);
                    }
                });
                return this.value.get();
            }
            return old;
        } finally {
            this.lock.unlock();
        }
    }
}
