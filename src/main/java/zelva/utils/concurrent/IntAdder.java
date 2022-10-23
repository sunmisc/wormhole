package zelva.utils.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @see java.util.concurrent.atomic.LongAdder
 * @author ZelvaLea
 */
@Deprecated
public class IntAdder {
    static final int START_CAPACITY = 2;
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    public final ConcurrentArrayCells<AtomicInteger> cells
            = new ConcurrentArrayCells<>(START_CAPACITY);


    static int spread(long h) {
        h ^= h << 13;
        h ^= h >>> 17;
        h ^= h << 5;
        return (int) h;
    }


    public void add(int delta) {
        Thread current = Thread.currentThread();
        int h = spread(current.threadId());

        AtomicInteger prev = cells.get(h & (cells.length() - 1));

        if (prev == null) {
            prev = cells.cae(
                    h & (cells.length() - 1),
                    null,
                    new AtomicInteger(delta)
            );
            if (prev != null) {
                int sz = cells.length();
                if (sz < NCPU) {
                    cells.resize(x -> x < NCPU ? x << 1 : x);
                }
            } else {
                return;
            }
        }
        prev.getAndAdd(delta);
    }

    public void reset() {
        for (AtomicInteger c : cells) {
            if (c == null) continue;
            c.set(0);
        }
    }

    public long get() {
        long sum = 0;
        for (AtomicInteger c : cells) {
            if (c == null) continue;
            sum += c.get();
        }
        return sum;
    }

}
