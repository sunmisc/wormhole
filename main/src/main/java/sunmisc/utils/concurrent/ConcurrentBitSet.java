package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * @see sunmisc.utils.concurrent.sets.ConcurrentBitSet
 */
@Deprecated
@SuppressWarnings("forRemoval")
public class ConcurrentBitSet {
    private static final int ADDRESS_BITS_PER_CELL
            = Integer.numberOfTrailingZeros(Long.SIZE);
    private static final int BITS_PER_CELL =
            1 << ADDRESS_BITS_PER_CELL;

    /* Used to shift left or right for a partial word mask */
    private static final long CELL_MASK = -1;
    private volatile Object[] cells;
    private boolean busy;

    public ConcurrentBitSet() {
        this(2);
    }

    public ConcurrentBitSet(final int init_nbits) {
        this.cells = new Object[init_nbits];
    }

    public boolean get(final int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException(
                    "bitIndex < 0: " + bitIndex);
        }
        final int index = cellIndex(bitIndex);
        for (Object[] cs = this.cells; index < cs.length; ) {
            final Object o = cs[index];
            if (o instanceof final Object[] ncs) {
                cs = ncs;
            } else {
                return o instanceof final Cell r &&
                        (r.value & (1L << bitIndex)) != 0;
            }
        }
        return false;
    }
    public boolean set(final int bitIndex) {
        final int index = cellIndex(bitIndex);
        final long mask = 1L << bitIndex;
        return (Objects.requireNonNull(this.putVal(index, mask,
                x -> x.getAndBitwiseOrRelease(mask))).value & mask) == 0;
    }


    public void clear(final int bitIndex) {
        final int index = cellIndex(bitIndex);
        final long mask = ~(1L << bitIndex);

        this.putVal(index, null,
                x -> x.getAndBitwiseAndRelease(mask));
    }

    public void flip(final int bitIndex) {
        final int index = cellIndex(bitIndex);
        final long mask = (1L << bitIndex);

        this.putVal(index, mask,
                x -> x.getAndBitwiseXorRelease(mask));
    }
    public int nextSetBit(final int fromIndex) {
        final int start = cellIndex(fromIndex);
        Object[] cs = this.cells;
        for (int i = start; i < cs.length; ++i) {
            for (Object o = cs[i]; ; ) {
                if (o instanceof final Object[] ncs) {
                    cs = this.cells;
                    o = ncs[i];
                } else {
                    long val = o instanceof final Cell r ? r.value : 0;
                    if (i == start) {
                        val &= (CELL_MASK << fromIndex);
                    }
                    if (val != 0) {
                        return i * BITS_PER_CELL +
                                Long.numberOfTrailingZeros(val);
                    } else {
                        break;
                    }
                }
            }
        }
        return -1;
    }
    public int nextClearBit(final int fromIndex) {
        final int start = cellIndex(fromIndex);
        int i;
        Object[] cs = this.cells;
        for (i = start; i < cs.length; ++i) {
            for (Object o = cs[i]; ; ) {
                if (o instanceof final Object[] ncs) {
                    cs = this.cells; o = ncs[i];
                } else {
                    long val = o instanceof final Cell r ? ~r.value
                            : CELL_MASK;
                    if (i == start) {
                        val &= (CELL_MASK << fromIndex);
                    }
                    if (val != 0) {
                        return i * BITS_PER_CELL +
                                Long.numberOfTrailingZeros(val);
                    } else {
                        break;
                    }
                }
            }
        }
        return start * BITS_PER_CELL;
    }
    public int cardinality() {
        int sum = 0;
        Object[] cs = this.cells;
        for (int i = 0; i < cs.length; ++i) {
            for (Object o = cs[i]; ; ) {
                if (o instanceof final Object[] ncs) {
                    cs = this.cells;
                    o = ncs[i];
                } else if (o instanceof final Cell r) {
                    sum += Long.bitCount(r.value);
                    break;
                }
            }
        }
        return sum;
    }
    private Cell putVal(final int h, final Long value,
                        final Consumer<Cell> consumer) {
        final int minCap = h + 1;
        boolean expand = false;
        for (Object[] cs = this.cells; ; ) {
            final int n = cs.length;
            if (expand) {
                if (n < minCap) {
                    try {
                        final int newLen = Math.max(minCap, n << 1);
                        final Object[] newArray = new Object[newLen];

                        for (int i = 0; i < n; ++i) {
                            Object o = cs[i];
                            if (o == null || o instanceof Object[]) {
                                final Object p = AA.compareAndExchange(cs,
                                        i, o, newArray);

                                if (p == null) {
                                    continue;
                                } else {
                                    o = p;
                                }
                            }
                            if (o != cs) {
                                newArray[i] = o;
                            }
                        }
                        Cell x = null;
                        if (value != null) {
                            x = new Cell(value);
                            newArray[h] = x;
                        }
                        this.cells = newArray;
                        return x;
                    } finally {
                        BUSY.setOpaque(this, false);
                    }
                }
            } else if (n < minCap) {
                if (BUSY.weakCompareAndSet(this, false, true)) {
                    expand = true;
                }
                cs = this.cells;
            } else {
                Object x = cs[h];

                if (x instanceof final Object[] ncs &&
                        cs != ncs) {
                    cs = ncs;
                    continue;
                } else if (x == null) {
                    if (value != null) {
                        final Cell newCell = new Cell(value);
                        if ((x = AA.compareAndExchange(
                                cs, h, null, newCell)) == null) {
                            return newCell;
                        }
                    } else {
                        return null;
                    }
                }
                if (x instanceof final Cell r) {
                    consumer.accept(r);
                    return r;
                }
            }
        }
    }
    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner(
                ", ",
                "[", "]");
        for (int i = 0;;) {
            final int t = this.nextSetBit(i);
            if (t < 0) {
                break;
            }
            joiner.add(Integer.toString(t));
            i = t + 1;
        }

        return joiner.toString();
    }

    private int recalculateWordsInUse() {
        final Object[] cs = this.cells;
        for (int i = cs.length-1; i >= 0; --i) {
            for (Object o = cs[i]; ; ) {
                if (o instanceof final Object[] ncs) {
                    o = ncs[i];
                } else if (o instanceof final Cell r) {
                    if (r.value == 0) {
                        break;
                    }
                    return i + 1;
                }
            }
        }
        return 0;
    }


    static int cellIndex(final int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_CELL;
    }

    private static final class Cell {

        volatile long value;

        Cell(final long value) {
            this.value = value;
        }

        void getAndBitwiseAndRelease(final long c) {
            VAL.getAndBitwiseAndRelease(this, c);
        }

        void getAndBitwiseOrRelease(final long c) {
            VAL.getAndBitwiseOrRelease(this, c);
        }
        void getAndBitwiseXorRelease(final long c) {
            VAL.getAndBitwiseXorRelease(this, c);
        }
        // VarHandle mechanics
        private static final VarHandle VAL;
        static {
            try {
                final MethodHandles.Lookup l = MethodHandles.lookup();
                VAL = l.findVarHandle(Cell.class,
                        "value", long.class);
            } catch (final ReflectiveOperationException e) {
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
            final MethodHandles.Lookup l = MethodHandles.lookup();
            BUSY = l.findVarHandle(ConcurrentBitSet.class,
                    "busy", boolean.class);
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}