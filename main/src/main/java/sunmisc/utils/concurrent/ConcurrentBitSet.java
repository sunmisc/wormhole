package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.BitSet;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

public class ConcurrentBitSet {
    private static final int ADDRESS_BITS_PER_CELL
            = Integer.numberOfTrailingZeros(Long.SIZE);
    private static final int BITS_PER_CELL =
            1 << ADDRESS_BITS_PER_CELL;

    /* Used to shift left or right for a partial word mask */
    private static final long CELL_MASK = -1;
    private volatile Object[] cells;

    private volatile int size; // todo:
    private boolean busy;

    public ConcurrentBitSet() {
        this(2);
    }

    public ConcurrentBitSet(int init_nbits) {
        this.cells = new Object[init_nbits];
    }

    public boolean get(int bitIndex) {
        if (bitIndex < 0)
            throw new IndexOutOfBoundsException(
                    "bitIndex < 0: " + bitIndex);
        final int index = cellIndex(bitIndex);
        for (Object[] cs = cells; index < cs.length; ) {
            Object o = cs[index];
            if (o instanceof Object[] ncs)
                cs = ncs;
            else
                return o instanceof Cell r &&
                        (r.value & (1L << bitIndex)) != 0;
        }
        return false;
    }
    public void set(int bitIndex) {
        final int index = cellIndex(bitIndex);
        final long mask = 1L << bitIndex;
        putVal(index, mask,
                x -> x.getAndBitwiseOrRelease(mask));
    }


    public void clear(int bitIndex) {
        final int index = cellIndex(bitIndex);
        final long mask = ~(1L << bitIndex);

        putVal(index, null,
                x -> x.getAndBitwiseAndRelease(mask));
    }

    public void flip(int bitIndex) {
        final int index = cellIndex(bitIndex);
        final long mask = (1L << bitIndex);

        putVal(index, mask,
                x -> x.getAndBitwiseXorRelease(mask));
    }
    public int nextSetBit(int fromIndex) {
        final int start = cellIndex(fromIndex);
        Object[] cs = cells;
        for (int i = start; i < cs.length; ++i) {
            for (Object o = cs[i]; ; ) {
                if (o instanceof Object[] ncs) {
                    cs = cells; o = ncs[i];
                } else {
                    long val = o instanceof Cell r ? r.value : 0;
                    if (i == start)
                        val &= (CELL_MASK << fromIndex);
                    if (val != 0)
                        return i * BITS_PER_CELL +
                                Long.numberOfTrailingZeros(val);
                    else
                        break;
                }
            }
        }
        return -1;
    }
    public int nextClearBit(int fromIndex) {
        int start = cellIndex(fromIndex);
        Object[] cs = cells;
        for (int i = start; i < cs.length; ++i) {
            for (Object o = cs[i]; ; ) {
                if (o instanceof Object[] ncs) {
                    cs = cells; o = ncs[i];
                } else {
                    long val = o instanceof Cell r ? ~r.value
                            : CELL_MASK;
                    if (i == start)
                        val &= (CELL_MASK << fromIndex);
                    if (val != 0)
                        return i * BITS_PER_CELL +
                                Long.numberOfTrailingZeros(val);
                    else
                        break;
                }
            }
        } // todo:
        return fromIndex;
    }
    public int cardinality() {
        int sum = 0;
        Object[] cs = cells;
        for (int i = 0; i < cs.length; ++i) {
            for (Object o = cs[i]; ; ) {
                if (o instanceof Object[] ncs) {
                    cs = cells;
                    o = ncs[i];
                } else if (o instanceof Cell r) {
                    sum += Long.bitCount(r.value);
                    break;
                }
            }
        }
        return sum;
    }
    private Cell putVal(int h, Long value,
                        Consumer<Cell> consumer) {
        final int wordsRequired = h + 1;
        boolean expand = false;
        for (Object[] cs = cells; ; ) {
            int n = cs.length;
            if (expand) {
                if (n < wordsRequired) {
                    try {
                        int newLen = Math.max(wordsRequired, n << 1);
                        Object[] newArray = new Object[newLen];

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
                        Cell x = null;
                        if (value != null) {
                            x = new Cell(value);
                            newArray[h] = x;
                        }
                        cells = newArray;
                        return x;
                    } finally {
                        BUSY.setOpaque(this, false);
                    }
                }
            } else if (n < wordsRequired) {
                if (BUSY.weakCompareAndSet(this, false, true))
                    expand = true;
                cs = cells;
            } else {
                Object x = cs[h];

                if (x instanceof Object[] ncs &&
                        cs != ncs) {
                    cs = ncs;
                    continue;
                } else if (x == null) {
                    if (value != null) {
                        Cell newCell = new Cell(value);
                        if ((x = AA.compareAndExchange(
                                cs, h, null, newCell)) == null)
                            return newCell;
                    } else
                        return null;
                }
                if (x instanceof Cell r) {
                    consumer.accept(r);
                    return r;
                }
            }
        }
    }
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(
                ", ",
                "[",
                "]");
        for (int i = 0;;) {
            int t = nextSetBit(i);
            if (t < 0)
                break;
            joiner.add(Integer.toString(t));
            i = t + 1;
        }

        return joiner.toString();
    }

    static int cellIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_CELL;
    }

    private static final class Cell {

        volatile long value;

        Cell(long value) {
            this.value = value;
        }

        void getAndBitwiseAndRelease(long c) {
            VAL.getAndBitwiseAndRelease(this, c);
        }

        void getAndBitwiseOrRelease(long c) {
            VAL.getAndBitwiseOrRelease(this, c);
        }
        void getAndBitwiseXorRelease(long c) {
            VAL.getAndBitwiseXorRelease(this, c);
        }
        // VarHandle mechanics
        private static final VarHandle VAL;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VAL = l.findVarHandle(Cell.class,
                        "value", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    // VarHandle mechanics
    private static final VarHandle AA =
            MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle BUSY;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BUSY = l.findVarHandle(ConcurrentBitSet.class,
                    "busy", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}