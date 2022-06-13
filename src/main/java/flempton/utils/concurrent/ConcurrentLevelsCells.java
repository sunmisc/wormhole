package flempton.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * @see ConcurrentArrayCells
 * @author Flempton
 */
public class ConcurrentLevelsCells {
    volatile QCells[] levels = new QCells[0];
    static final int SIZE_CHUNK = 8;

    public Object get(int i) {
        int level = i / SIZE_CHUNK, index = i % SIZE_CHUNK;
        QCells q = levels[level];
        return q.arrayAt(index);
    }
    public boolean cas(int i, Object c, Object v) {
        int lvl = i / SIZE_CHUNK, idx = i % SIZE_CHUNK;
        return tryGrow(lvl).casAt(idx,c,v);
    }
    public Object set(int i, Object c) {
        int lvl = i / SIZE_CHUNK, idx = i % SIZE_CHUNK;
        return tryGrow(lvl).getAndSet(idx,c);
    }
    QCells tryGrow(int level) {
        for (QCells[] lvs;;) {
            if (level >= (lvs = levels).length - 1) {
                if (!LEVELS.compareAndSet(this,
                        lvs, lvs = newLevels(lvs, level + 4))) {
                    continue;
                }
            }
            return lvs[level];
        }
    }
    public int length() {
        return levels.length * SIZE_CHUNK;
    }

    record QCells(Object[] array) {
        static QCells of() {
            return new QCells(new Object[SIZE_CHUNK]);
        }
        Object arrayAt(int i) {
            return AA.getAcquire(array, i);
        }
        Object getAndSet(int i, Object v) {
            return AA.getAndSet(array, i, v);
        }
        boolean casAt(int i, Object c, Object v) {
            return AA.compareAndSet(array, i, c, v);
        }
        private static final VarHandle AA
                = MethodHandles.arrayElementVarHandle(Object[].class);
    }
    static QCells[] newLevels(QCells[] prev, int newSz) {
        int n = prev.length;
        prev = Arrays.copyOf(prev, newSz);

        for (; n < newSz; ++n) {
            prev[n] = QCells.of();
        }
        return prev;
    }

    @Override
    public String toString() {
        QCells[] lvs = levels; int n;
        if ((n = lvs.length) == 0)
            return "[]";
        n *= SIZE_CHUNK;
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (QCells q : lvs) {
            for (int i = 0; i < SIZE_CHUNK; ++i) {
                Object f = q.arrayAt(i);
                sb.append(f);
                if (n-- == 1)
                    return sb.append(']').toString();
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private static final VarHandle LEVELS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            LEVELS = l.findVarHandle(ConcurrentLevelsCells.class, "levels", QCells[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}