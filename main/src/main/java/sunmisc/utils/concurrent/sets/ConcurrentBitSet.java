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
    public boolean add(final Integer bitIndex) {
        final int index = cellIndex(bitIndex);

        if (this.memory.length() <= bitIndex) {
            this.memory.realloc(this.ctl.updateAndGet(
                    p -> Math.max(p, bitIndex + 1)));
        }
        final long mask = 1L << bitIndex;
        return (this.memory.fetchAndBitwiseOr(index, mask) & mask) == 0;
    }
    @Override
    public boolean remove(final Object o) {
        final int bitIndex = (int) o;
        final int index = cellIndex(bitIndex);

        if (index < this.ctl.get()) {
            final long mask = 1L << bitIndex;
            return (this.memory.fetchAndBitwiseAnd(index, ~mask) & mask) != 0;
        }
        return false;
    }

    @Override
    public boolean contains(final Object o) {
        final int bitIndex = (int) o;
        final int index = cellIndex(bitIndex);
        return index < this.ctl.get() &&
                (this.memory.fetch(index) & (1L << bitIndex)) != 0;
    }


    private int lastCell() {
        return this.ctl.getAcquire();
    }
    @Override
    public int size() {
        return lastCell() * BITS_PER_CELL;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0, n = lastCell(); i < n; ++i) {
            if (this.memory.fetch(i) != 0) {
                return false;
            }
        }
        return true;
    }
    public int cardinality() {
        int sum = 0;
        for (int i = 0, n = lastCell(); i < n; ++i) {
            sum += Long.bitCount(this.memory.fetch(i));
        }
        return sum;
    }

    @Override
    public final void clear() {
        final int n = this.ctl.get();
        for (int i = 0; i < n; ++i) {
            this.memory.store(i,0L);
        }
        this.ctl.compareAndSet(n, 0);
    }


    private static int cellIndex(final int bitIndex) {
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
    public Iterator<Integer> iterator(final int index) {
        Objects.checkIndex(index, lastCell());

        return new Cursor.CursorAsIterator<>(
                new CursorImpl(this, index)
        );
    }

    private int nextSetBit(final int fromIndex) {
        int u = cellIndex(fromIndex);
        if (u >= this.memory.length()) {
            throw new IndexOutOfBoundsException();
        }
        for (long word = this.memory.fetch(u) & (-1L << fromIndex);;) {
            if (word != 0) {
                return (u * BITS_PER_CELL) + Long.numberOfTrailingZeros(word);
            } else if (++u >= this.ctl.get()) {
                return -1;
            }
            word = this.memory.fetch(u);
        }
    }

    @Override
    public int hashCode() {
        long h = 1234;
        for (int i = lastCell(); --i >= 0; ) {
            h ^= this.memory.fetch(i) * (i + 1);
        }
        return Long.hashCode(h);
    }

    private record CursorImpl(
            ConcurrentBitSet bitSet,
            int nextSetBit
    ) implements Cursor<Integer> {

        @Override
        public boolean exists() {
            return this.nextSetBit >= 0;
        }

        @Override
        public Cursor<Integer> next() {
            final int p = this.nextSetBit;
            if (p < 0) {
                throw new IllegalStateException();
            }
            return new CursorImpl(this.bitSet, this.bitSet.nextSetBit(p + 1));
        }

        @Override
        public Integer element() {
            final int p = this.nextSetBit;
            if (p < 0) {
                throw new IllegalStateException();
            }
            return p;
        }

        @Override
        public void remove() {
            final int p = this.nextSetBit;
            if (p < 0) {
                throw new IllegalStateException();
            }
            this.bitSet.remove(p);
        }
    }
}
