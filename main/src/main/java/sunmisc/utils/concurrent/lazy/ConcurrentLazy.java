package sunmisc.utils.concurrent.lazy;

import sunmisc.utils.Scalar;
import sunmisc.utils.lazy.Lazy;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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
public final class ConcurrentLazy<V, E extends Throwable> implements Lazy<V,E> {
    // private volatile Object outcome;
    private volatile Lazy<V,E> outcome;

    public ConcurrentLazy(final Scalar<V, E> scalar) {
        final class Sync extends ReentrantLock implements Lazy<V,E> {
            @Override
            public V value() throws E {
                lock();
                try {
                    if (completed())
                        return ConcurrentLazy.this.value();
                    final V val = scalar.value();
                    // outcome = val;
                    outcome = new Lazy<>() {
                        @Override
                        public V value() { return val; }
                        @Override
                        public boolean completed() { return true; }
                        @Override
                        public String toString() { return Objects.toString(val); }
                    };
                    return val;
                } finally {
                    unlock();
                }
            }

            @Override
            public boolean completed() {
                final Lazy<V,E> lazy = outcome;
                return lazy != this && lazy.completed();
            }
            @Override
            public String toString() {
                return "uninitialized";
            }
        }
        this.outcome = new Sync();
    }


    @Override
    public V value() throws E {
        return outcome.value();
    }

    @Override
    public boolean completed() {
        return outcome.completed();
    }
    @Override
    public String toString() {
        return outcome.toString();
    }

}