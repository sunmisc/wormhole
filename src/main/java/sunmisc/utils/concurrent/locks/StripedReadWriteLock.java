package sunmisc.utils.concurrent.locks;

import jdk.internal.vm.annotation.Contended;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

public class StripedReadWriteLock { // todo: ReadWriteLock
    private volatile StripedReaders adder = new StripedReaders();
    private final Object monitor = new Object();

    public <T> T readLock(Supplier<T> supplier) {
        for (StripedReaders a;;) {
            if ((a = adder) == null) {
                synchronized (monitor) {
                    return supplier.get();
                }
            } else {
                Node x = a.inc();
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
            StripedReaders p = (StripedReaders)
                    ADDER.getAndSet(this, null);
            while (p.waiters() != 0)
                Thread.onSpinWait();
            try {
                return supplier.get();
            } finally {
                adder = p;
            }
        }
    }

    @Contended static final class Node {
        volatile int readers;

        Node(int v) { this.readers = v; }

        private void getAndAdd(int delta) {
            READERS.getAndAddRelease(this, delta);
        }

        @Override
        public String toString() {
            return Integer.toString(readers);
        }
    }
    // VarHandle mechanics
    private static final VarHandle ADDER, READERS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ADDER = l.findVarHandle(StripedReadWriteLock.class,
                    "adder", StripedReaders.class);
            READERS = l.findVarHandle(Node.class,
                    "readers", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    static class StripedReaders {
        private final Node[] nodes = new Node[8]; // todo: grow


        static int spread(long h) {
            h ^= h << 13;
            h ^= h >>> 17;
            h ^= h << 5;
            return (int) h;
        }


        private Node inc() {
            Thread thread = Thread.currentThread();
            Node[] items = nodes;
            int h = spread(thread.threadId()) & (items.length-1);

            Node x = items[h];
            if (x == null) {
                Node newCell = new Node(1);
                if ((x = (Node) AA.compareAndExchange(
                        items, h, null, newCell)) == null)
                    return newCell;
            }
            x.getAndAdd(1);
            return x;
        }

        public int waiters() {
            int sum = 0;
            for (Node x : nodes) {
                if (x == null)
                    continue;
                sum += x.readers;
            }
            return sum;
        }
        private static final VarHandle AA
                = MethodHandles.arrayElementVarHandle(Node[].class);

    }

}