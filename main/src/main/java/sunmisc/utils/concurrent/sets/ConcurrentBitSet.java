package sunmisc.utils.concurrent.sets;

import sunmisc.utils.Cursor;
import sunmisc.utils.concurrent.memory.BitwiseModifiableMemory;
import sunmisc.utils.concurrent.memory.BitwiseSegmentMemory;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentBitSet
        extends AbstractSet<Integer>
        implements Set<Integer> {

    private static final int ADDRESS_BITS_PER_CELL
            = Integer.numberOfTrailingZeros(Long.SIZE);
    private static final int BITS_PER_CELL =
            1 << ADDRESS_BITS_PER_CELL;
    private final BitwiseModifiableMemory<Long> memory
            = new BitwiseSegmentMemory<>(long.class);
    private final AtomicInteger ctl = new AtomicInteger();

    @Override
    public boolean add(Integer bitIndex) {
        final int index = cellIndex(bitIndex);

        if (memory.length() <= bitIndex)
            memory.realloc(ctl.updateAndGet(
                    p -> Math.max(p, bitIndex + 1)));
        final long mask = 1L << bitIndex;
        return (memory.fetchAndBitwiseOr(index, mask) & mask) == 0;
    }
    @Override
    public boolean remove(Object o) {
        final int bitIndex = (int) o;
        final int index = cellIndex(bitIndex);

        if (index < ctl.get()) {
            long mask = 1L << bitIndex;
            return (memory.fetchAndBitwiseAnd(index, ~mask) & mask) != 0;
        }
        return false;
    }

    @Override
    public boolean contains(Object o) {
        final int bitIndex = (int) o;
        final int index = cellIndex(bitIndex);
        return index < ctl.get() &&
                (memory.fetch(index) & (1L << bitIndex)) != 0;
    }


    private int lastCell() {
        return ctl.getAcquire();
    }
    @Override
    public int size() {
        return lastCell() * BITS_PER_CELL;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0, n = lastCell(); i < n; ++i) {
            if (memory.fetch(i) != 0)
                return false;
        }
        return true;
    }
    public int cardinality() {
        int sum = 0;
        for (int i = 0, n = lastCell(); i < n; ++i)
            sum += Long.bitCount(memory.fetch(i));
        return sum;
    }

    @Override
    public final void clear() {
        int n = ctl.get();
        for (int i = 0; i < n; ++i)
            memory.store(i,0L);
        ctl.compareAndSet(n, 0);
    }


    private static int cellIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_CELL;
    }

    @Override
    public Iterator<Integer> iterator() {
        final int i = nextSetBit(0);
        return new Cursor.CursorAsIterator<>(i < 0
                ? Cursor.empty()
                : new CursorImpl(this, i)
        );
    }
    public Iterator<Integer> iterator(int index) {
        Objects.checkIndex(index, lastCell());

        return new Cursor.CursorAsIterator<>(
                new CursorImpl(this, index)
        );
    }

    private int nextSetBit(int fromIndex) {
        int u = cellIndex(fromIndex);
        if (u >= memory.length())
            throw new IndexOutOfBoundsException();
        for (long word = memory.fetch(u) & (-1L << fromIndex);;) {
            if (word != 0)
                return (u * BITS_PER_CELL) + Long.numberOfTrailingZeros(word);
            else if (++u >= ctl.get())
                return -1;
            word = memory.fetch(u);
        }
    }

    @Override
    public int hashCode() {
        long h = 1234;
        for (int i = lastCell(); --i >= 0; )
            h ^= memory.fetch(i) * (i + 1);
        return Long.hashCode(h);
    }

    private record CursorImpl(
            ConcurrentBitSet bitSet,
            int nextSetBit
    ) implements Cursor<Integer> {

        @Override
        public boolean exists() {
            return nextSetBit >= 0;
        }

        @Override
        public Cursor<Integer> next() {
            final int p = nextSetBit;
            if (p < 0) throw new IllegalStateException();
            return new CursorImpl(bitSet, bitSet.nextSetBit(p + 1));
        }

        @Override
        public Integer element() {
            final int p = nextSetBit;
            if (p < 0) throw new IllegalStateException();
            return p;
        }

        @Override
        public void remove() {
            final int p = nextSetBit;
            if (p < 0) throw new IllegalStateException();
            bitSet.remove(p);
        }
    }
}
