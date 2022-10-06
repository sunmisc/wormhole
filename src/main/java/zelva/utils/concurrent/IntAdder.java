package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @see java.util.concurrent.atomic.LongAdder
 * @author ZelvaLea
 */
@Deprecated
public class IntAdder {
    static final int START_CAPACITY = 2;
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    private int sizeCtl = START_CAPACITY;

    public final ConcurrentArrayCells<AtomicInteger> cells
            = new ConcurrentArrayCells<>(START_CAPACITY);


    static int hash(Thread o) {
        int h = System.identityHashCode(o);
        return ((h << 1) - (h << 8));
    }


    public void add(int delta) {
        Thread current = Thread.currentThread();
        int h = hash(current);
        AtomicInteger prev = cells.get(h & (cells.length() - 1));

        if (prev == null) {
            prev = cells.cae(
                    h & (cells.length() - 1),
                    null,
                    new AtomicInteger(delta)
            );
            if (prev != null) {
                for (int sz, nx;;) {
                    if ((sz = cells.length()) >= NCPU) {
                        break;
                    } else if (SIZECTL.weakCompareAndSet(this, sz, nx = sz << 1)) {
                        cells.resize(nx);
                    }
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

    private static final VarHandle SIZECTL;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SIZECTL = l.findVarHandle(IntAdder.class, "sizeCtl", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
