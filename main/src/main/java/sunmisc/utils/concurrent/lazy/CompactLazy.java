package sunmisc.utils.concurrent.lazy;

import sunmisc.utils.lazy.Lazy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

public final class CompactLazy<E> implements Lazy<E> {

    private volatile Object outcome;


    public CompactLazy(final Supplier<E> supplier) {
        this.outcome = new Sync(supplier);
    }


    private final class Sync implements Supplier<E> {

        private final Supplier<E> supplier;
        private volatile WaitNode waiters;

        private Sync(Supplier<E> supplier) {
            this.supplier = supplier;
        }

        @Override
        public E get() {
            for (WaitNode q = new WaitNode(), x;;) {
                if (isDone()) {
                    return CompactLazy.this.get();
                } else if ((x = waiters) == null) {
                    if (WAITERS.weakCompareAndSet(this, null, q) && !isDone()) {
                        try {
                            outcome = supplier.get();
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
    public boolean isDone() {
        final Object o = outcome;
        return o == null || !o.getClass().isAssignableFrom(Sync.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get() {
        final Object x = outcome;
        return (E)(x != null && x.getClass().isAssignableFrom(Sync.class)
                ? ((Sync)x).get() : x);
    }
    private static final class WaitNode {
        volatile WaitNode next;
        volatile Thread thread;

        WaitNode() { thread = Thread.currentThread(); }


        // VarHandle mechanics
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
