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


    static int spread(int h) {
        return h ^ (h >>> 16);
    }


    public void add(int delta) {
        Thread current = Thread.currentThread();
        int h = spread(current.hashCode());

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
                    cells.resize((x -> x << 1));
                }
            } else {
                return;
            }
        }
        prev.getAndAdd(delta);
    }

    public int get() {
        int sum = 0;
        for (AtomicInteger c : cells) {
            if (c == null) continue;
            sum += c.get();
        }
        return sum;
    }

}
