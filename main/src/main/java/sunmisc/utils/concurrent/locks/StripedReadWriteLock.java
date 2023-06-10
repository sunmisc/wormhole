package sunmisc.utils.concurrent.locks;

import sunmisc.utils.concurrent.UnblockingArrayBuffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

/**
 * This implementation is much better than currently
 * available lock mechanisms - ReadWriteLock for a very short read
 * low contention is achieved with a random access table of threads
 * the size of this dynamic table depends on the hardware number of cores
 * To save memory, the table expands with possible races by one cell,
 * the principle is similar to
 * @see UnblockingArrayBuffer
 * except for optimizations specifically for our case
 * Our table has the following properties:
 * Table expands and cannot be reduced
 * need to provide atomic lazy initialization of each cell
 *
 * @author Sunmisc Unsafe
 */
public class StripedReadWriteLock { // todo: ReadWriteLock
    private volatile Striped32 adder;
    private final Object monitor = new Object();

    public StripedReadWriteLock() {
        adder = new Striped32();
    }

    public <T> T readLock(Supplier<T> supplier) {
        for (Striped32 a;;) {
            if ((a = adder) == null) {
                synchronized (monitor) {
                    return supplier.get();
                }
            } else {
                Cell x = a.inc(1);
                if (adder == null)
                    x.getAndAdd(-1); // signal
                else {
                    T result = supplier.get();
                    x.getAndAdd(-1);
                    return result;
                }
            }
        }

    }
    public <T> T writeLock(Supplier<T> supplier) {
        synchronized (monitor) {
            Striped32 p = (Striped32) ADDER.get(this);
            // check for null on arm?
            adder = null;
            while (p.isReadLocked())
                Thread.onSpinWait();
            try {
                return supplier.get();
            } finally {
                adder = p;
            }
        }
    }
    static final class Cell {
        volatile int readers;

        Cell(int init) {
            this.readers = init;
        }

        int getAndAdd(int delta) {
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
    public static class Striped32 {
        static final int MAX_CAPACITY = 16;
        static final int NCPU = Math.min(
                MAX_CAPACITY,
                Runtime.getRuntime().availableProcessors());

        private volatile Object[] cells;
        private boolean busy;


        int getProbe() {
            return (int) Thread.currentThread().threadId();
        }

        public Cell inc(int delta) {
            final int probe = getProbe();
            Object[] cs = cells;
            if (cs == null) {
                Object[] rs = new Object[2];
                Cell x;
                rs[probe & 1] = x = new Cell(delta);

                if ((cs = (Object[]) CELLS.compareAndExchange(
                        this, null, rs)) == null)
                    return x;
            }
            for (;;) {
                int n = cs.length, h = probe & (n - 1);
                Object x = cs[h];

                if (x instanceof Object[] ncs &&
                        cs != ncs) {
                    cs = ncs;
                    continue;
                } else if (x == null) {
                    Cell newCell = new Cell(delta);
                    if ((x = AA.compareAndExchange(
                            cs, h, null, newCell)) == null)
                        return newCell;
                }
                if (x instanceof Cell r) {
                    if (r.getAndAdd(delta) > 0 &&
                            n < NCPU && !busy &&
                            BUSY.compareAndSet(this, false, true)) {
                        try {
                            if (cs == cells) {
                                Object[] newArray = new Object[n << 1];

                                for (int i = 0; i < n; ++i) {
                                    Object o = cs[i];
                                    if (o == null || o instanceof Object[]) {
                                        Object p = AA.compareAndExchange(cs,
                                                i, o, newArray);

                                        if (p == null)
                                            continue;
                                        else
                                            o = p;
                                    }
                                    if (o != cs)
                                        newArray[i] = o;
                                }
                                cells = newArray;
                            }
                        } finally {
                            busy = false;
                        }
                    }
                    return r;
                }
            }
        }

        public boolean isReadLocked() {
            Object[] cs = cells;
            if (cs != null) {
                for (int i = 0, n = cs.length; i < n; ++i) {
                    for (Object o = cs[i];;) {
                        if (o instanceof Object[] ncs)
                            o = ncs[i];
                        else {
                            if (o instanceof Cell r &&
                                    r.readers != 0)
                                return true;
                            break;
                        }
                    }
                }
            }
            return false;
        }
        private static final VarHandle AA
                = MethodHandles.arrayElementVarHandle(Object[].class);

        // VarHandle mechanics
        private static final VarHandle BUSY, CELLS;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                BUSY = l.findVarHandle(Striped32.class,
                        "busy", boolean.class);
                CELLS = l.findVarHandle(Striped32.class,
                        "cells", Object[].class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

}