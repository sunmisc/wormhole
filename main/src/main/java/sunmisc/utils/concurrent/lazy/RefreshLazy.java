package sunmisc.utils.concurrent.lazy;

import sunmisc.utils.Scalar;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class RefreshLazy<V> implements Scalar<V> {
    private final Lock lock = new ReentrantLock();
    private final AtomicLong time = new AtomicLong(0);
    private final AtomicReference<V> value = new AtomicReference<>();
    private final Scalar<V> origin;
    private final Duration latency;

    public RefreshLazy(final Scalar<V> origin,
                       final Duration latency) {
        this.origin = origin;
        this.latency = latency;
    }

    @Override
    public V value() throws Exception {
        final long to = this.time.get();
        if (to >= System.nanoTime()) {
            return this.value.get();
        }
        this.lock.lock();
        try {
            final long now = System.nanoTime();
            if (this.time.getPlain() >= now) {
                return this.value.getPlain();
            } else {
                final V poll = this.origin.value();
                this.value.set(poll);
                this.time.set(now + this.latency.toNanos());
                return poll;
            }
        } finally {
            this.lock.unlock();
        }
    }
}
