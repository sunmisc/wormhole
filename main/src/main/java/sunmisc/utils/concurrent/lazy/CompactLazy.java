package sunmisc.utils.concurrent.lazy;

import sunmisc.utils.Scalar;
import sunmisc.utils.lazy.Lazy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;

final class CompactLazy<V, E extends Throwable> implements Lazy<V,E> {

    private volatile Object outcome;


    public CompactLazy(final Scalar<V,E> scalar) {
        this.outcome = new Sync(scalar);
    }


    private final class Sync implements Scalar<V,E> {

        private final Scalar<V,E> scalar;
        private volatile WaitNode waiters;

        private Sync(Scalar<V,E> scalar) {
            this.scalar = scalar;
        }

        @Override
        public V value() throws E {
            for (WaitNode q = new WaitNode(), x;;) {
                if (completed()) {
                    return CompactLazy.this.value();
                } else if ((x = waiters) == null) {
                    if (WAITERS.weakCompareAndSet(this, null, q)
                            && !completed()) {
                        try {
                            outcome = scalar.value();
                        } finally {
                            unlock();
                        }
                    }
                } else if (WAITERS.weakCompareAndSet(this, q.next = x, q))
                    LockSupport.park();
            }
        }
        private void unlock() {
            for (WaitNode q; (q = waiters) != null;) {
                if (WAITERS.weakCompareAndSet(this, q, null)) {
                    for (;;) {
                        Thread t = q.thread;
                        if (t != null) {
                            q.thread = null;
                            LockSupport.unpark(t);
                        }
                        WaitNode next = q.next;
                        if (next == null)
                            break;
                        q.next = null; // unlink to help gc
                        q = next;
                    }
                    break;
                }
            }
        }
    }
    @Override
    public boolean completed() {
        final Object o = outcome;
        return o == null || !o.getClass().isAssignableFrom(Sync.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V value() throws E {
        final Object x = outcome;
        return (V)(x != null && x.getClass().isAssignableFrom(Sync.class)
                ? ((Sync)x).value() : x);
    }
    private static final class WaitNode {
        volatile WaitNode next;
        volatile Thread thread;

        WaitNode() { thread = Thread.currentThread(); }
    }

    // VarHandle mechanics
    private static final VarHandle WAITERS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            WAITERS = l.findVarHandle(CompactLazy.Sync.class, "waiters", WaitNode.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.org/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;
    }
}
