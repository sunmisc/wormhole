package sunmisc.utils.concurrent.locks;

import jdk.internal.vm.annotation.Contended;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This implementation is much better than currently
 * available lock mechanisms - ReadWriteLock for a very short read
 * <p>
 * low contention is achieved with a random access table of threads
 * <p>
 * the size of this dynamic table depends on the hardware number of cores
 * <p>
 * To save memory, the table expands with possible races by one cell,
 * the principle is similar to UnblockingArrayBuffer,
 * except for optimizations specifically for our case
 * Our table has the following properties:
 * Table expands and cannot be reduced
 * need to provide atomic lazy initialization of each cell
 *
 * @author Sunmisc Unsafe
 */
public class StripedReadWriteLock { // todo: ReadWriteLock
    private volatile Striped32 adder
            = new Striped32();
    private final Object monitor = new Object();

    public <T> T readLock(Supplier<T> supplier) {
        for (Striped32 a;;) {
            if ((a = adder) == null) {
                synchronized (monitor) {
                    return supplier.get();
                }
            } else {
                Cell x = a.inc();
                if (adder == null) {
                    x.getAndAdd(-1); // signal
                } else {
                    T result = supplier.get();
                    x.getAndAdd(-1);
                    return result;
                }
            }
        }

    }
    public <T> T writeLock(Supplier<T> supplier) {
        synchronized (monitor) {
            Striped32 p = (Striped32)
                    ADDER.getAndSet(this, null);
            while (p.waiters() != 0)
                Thread.onSpinWait();
            try {
                return supplier.get();
            } finally {
                ADDER.set(this, p);
            }
        }
    }

    @Contended static final class Cell {
        volatile int readers;

        Cell(int v) { this.readers = v; }


        public int getAndAdd(int delta) {
            return (int) READERS.getAndAddRelease(this, delta);
        }

        @Override
        public String toString() {
            return Integer.toString(readers);
        }
        // VarHandle mechanics
        private static final VarHandle READERS;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                READERS = l.findVarHandle(Cell.class,
                        "readers", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    // VarHandle mechanics
    private static final VarHandle ADDER;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ADDER = l.findVarHandle(StripedReadWriteLock.class,
                    "adder", Striped32.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    static class Striped32 {
        static final int NCPU =
               Runtime.getRuntime().availableProcessors();
        private static final AtomicInteger probeGenerator
                = new AtomicInteger();
        private volatile Object[] cells = new Object[2];
        private volatile boolean busy;

        private final ThreadLocal<Integer> threadLocal =
                ThreadLocal.withInitial(probeGenerator::getAndIncrement);

        int getProbe() {
            return threadLocal.get();
        }

        private Cell inc() {
            boolean collide = false;
            for (Object[] cs = cells;;) {
                int n = cs.length, h = getProbe() & (n - 1);

                Object c = cs[h];

                if (c instanceof Object[] ncs && cs != ncs) {
                    cs = ncs;
                } else {
                    if (c == null) {
                        Cell newCell = new Cell(1);
                        if ((c = AA.compareAndExchange(
                                cs, h, null, newCell)) == null)
                            return newCell;
                        else
                            collide = true;
                    }
                    assert c instanceof Cell;
                    Cell r = (Cell) c;
                    if ((r.getAndAdd(1) > 0 || collide) &&
                            n < NCPU && !busy &&
                            BUSY.compareAndSet(this, false, true)) {
                        try {
                            Object[] newArray = new Object[n << 1];

                            for (int i = 0; i < n; ++i) {
                                Object o = cs[i];
                                //               assert o == cs
                                if (o == null || o instanceof Object[]) {
                                    Object p = AA.compareAndExchange(cs,
                                            i, o, newArray);

                                    if (p == o)
                                        continue;
                                    else
                                        o = p;
                                }
                                if (o != cs)
                                    newArray[i] = o;
                            }
                            cells = newArray;
                        } finally {
                            busy = false;
                        }
                    }
                    return r;
                }
            }
        }

        public int waiters() {
            int sum = 0;

            Object[] cs = cells;

            for (int i = 0, n = cs.length; i < n; ++i) {
                Object o = cs[i];
                if (o instanceof Object[] ncs)
                    o = ncs[i];

                if (o != null)
                    sum += ((Cell)o).readers;
            }
            return sum;
        }
        private static final VarHandle AA
                = MethodHandles.arrayElementVarHandle(Object[].class);

        // VarHandle mechanics
        private static final VarHandle BUSY;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                BUSY = l.findVarHandle(Striped32.class,
                        "busy", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

}