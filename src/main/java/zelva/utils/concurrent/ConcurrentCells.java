package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.function.Function;

/**
 * @see ConcurrentArrayCopy
 */
public class ConcurrentCells {
    volatile QCells[] levels = {QCells.of()};
    static final int MIN_CHUNK = 8;

    public Object get(int i) {
        int level = i / MIN_CHUNK, index = i % MIN_CHUNK;
        QCells q = levels[level];
        return q.arrayAt(index);
    }
    public boolean cas(int i, Object c, Object v) {
        int level = i / MIN_CHUNK, index = i % MIN_CHUNK;
        for (QCells[] lvs;;) {
            if (level >= (lvs = levels).length - 1) {
                if (!LEVELS.compareAndSet(this,
                        lvs, lvs = newLevels(lvs, level + 4))) {
                    continue;
                }
            }
            return lvs[level].casAt(index,c,v);
        }
    }
    public Object merge(int i, Function<Object,Object> function) {
        int level = i / MIN_CHUNK, index = i % MIN_CHUNK;
        for (QCells[] lvs;;) {
            if (level >= (lvs = levels).length-1) {
                if (!LEVELS.compareAndSet(this,
                        lvs, lvs = newLevels(lvs, level + 4))) {
                    continue;
                }
            }
            for (QCells q = lvs[level];;) {
                Object v = q.arrayAt(index);
                if (q.casAt(i,v,function.apply(v))) {
                    return v;
                }
            }
        }
    }
    public int length() {
        return levels.length * MIN_CHUNK;
    }

    record QCells(Object[] array) {
        static QCells of() {
            return new QCells(new Object[MIN_CHUNK]);
        }
        Object arrayAt(int i) {
            return AA.getAcquire(array, i);
        }
        void setAt(int i, Object v) {
            AA.setRelease(array, i, v);
        }
        Object getAndSet(int i, Object v) {
            return AA.getAndSet(array, i, v);
        }
        boolean casAt(int i, Object c, Object v) {
            return AA.compareAndSet(array, i, c, v);
        }
        @Override
        public String toString() {
            return Arrays.toString(array);
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
        QCells[] lvs = levels;
        if (lvs.length == 0)
            return "[]";
        StringBuilder builder = new StringBuilder();
        for (QCells q : lvs) {
            for (int i = 0; i < MIN_CHUNK; ++i) {
                builder.append(q.arrayAt(i)).append(", ");
            }
        }
        return builder.toString();
    }

    private static final VarHandle LEVELS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            LEVELS = l.findVarHandle(ConcurrentCells.class, "levels", QCells[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}