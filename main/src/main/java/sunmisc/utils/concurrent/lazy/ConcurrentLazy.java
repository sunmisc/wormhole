package sunmisc.utils.concurrent.lazy;

import sunmisc.utils.Scalar;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/*
 * This not very nice solution is due to footprint, at the moment jvm bloats
 * the footprint object a lot, this is a very delicate property -
 * one step to the left, one step to the right and the size of the object is
 * significantly larger than expected.
 * I'm focusing on the latest jdk versions, as soon as one of
 * the projects compresses objects (e.g. Valhalla, Liliput project)
 * the code will be rewritten.
 *
 * There is also an idea to write my own lightweight version of locking
 * on waiting for the first writer, but this idea may not be so promising
 */
public final class ConcurrentLazy<V, E extends Throwable> implements Scalar<V, E> {
    private final AtomicReference<Scalar<V, E>> outcome;

    public ConcurrentLazy(final Scalar<V, E> scalar) {
        final class Sync extends ReentrantLock implements Scalar<V, E> {
            @Override
            public V value() throws E {
                this.lock();
                try {
                    if (ConcurrentLazy.this.outcome.getPlain() != this) {
                        return ConcurrentLazy.this.value();
                    }
                    final V val = scalar.value();
                    ConcurrentLazy.this.outcome.set(new Scalar<>() {
                        @Override
                        public V value() {
                            return val;
                        }

                        @Override
                        public String toString() {
                            return Objects.toString(val);
                        }
                    });
                    return val;
                } finally {
                    this.unlock();
                }
            }

            @Override
            public String toString() {
                return "uninitialized";
            }
        }
        this.outcome = new AtomicReference<>(new Sync());
    }


    @Override
    public V value() throws E {
        return this.outcome.get().value();
    }

    @Override
    public String toString() {
        return this.outcome.get().toString();
    }
}