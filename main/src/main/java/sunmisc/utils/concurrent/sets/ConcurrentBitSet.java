package sunmisc.utils.concurrent.sets;

import sunmisc.utils.Cursor;
import sunmisc.utils.concurrent.memory.BitwiseModifiableMemory;
import sunmisc.utils.concurrent.memory.BitwiseSegmentsMemory;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class ConcurrentBitSet extends AbstractSet<Integer> implements Set<Integer> {
    private static final int ADDRESS_BITS_PER_CELL
            = Integer.numberOfTrailingZeros(Long.SIZE);
    private static final int BITS_PER_CELL =
            1 << ADDRESS_BITS_PER_CELL;
    private final AtomicReference<BitwiseModifiableMemory<Long>> memory =
            new AtomicReference<>(
                    new BitwiseSegmentsMemory<>(long.class, 4)
            );

    @Override
    public boolean add(final Integer value) {
        final int index = cellIndex(value);
        if (this.memory.get().length() <= value) {
            this.memory.updateAndGet(old -> {
                final int len = Math.max(old.length(), value + 1);
                return old.realloc(len);
            });
        }
        final long mask = 1L << value;
        return (this.memory.get().fetchAndBitwiseOr(index, mask) & mask) == 0;
    }

    @Override
    public boolean remove(final Object value) {
        final int bitIndex = (int) value;
        final int index = cellIndex(bitIndex);
        final BitwiseModifiableMemory<Long> mem = this.memory.get();
        if (index < mem.length()) {
            final long mask = 1L << bitIndex;
            return (mem.fetchAndBitwiseAnd(index, ~mask) & mask) != 0;
        }
        return false;
    }

    @Override
    public boolean contains(final Object o) {
        final int bitIndex = (int) o;
        final int index = cellIndex(bitIndex);
        final BitwiseModifiableMemory<Long> mem = this.memory.get();
        return index < mem.length() && (mem.fetch(index) & (1L << bitIndex)) != 0;
    }

    @Override
    public int size() {
        final BitwiseModifiableMemory<Long> mem = this.memory.get();
        final int n = mem.length();
        int sum = 0;
        for (int i = 0; i < n; ++i) {
            sum += Long.bitCount(mem.fetch(i));
        }
        return sum;
    }

    @Override
    public boolean isEmpty() {
        final BitwiseModifiableMemory<Long> mem = this.memory.get();
        final int n = mem.length();
        for (int i = 0; i < n; ++i) {
            if (mem.fetch(i) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void clear() {
        final BitwiseModifiableMemory<Long> mem = this.memory.get();
        final int n = mem.length();
        for (int i = 0; i < n; ++i) {
            mem.store(i,0L);
        }
    }

    private static int cellIndex(final int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_CELL;
    }

    @Override
    public Iterator<Integer> iterator() {
        final int i = this.nextSetBit(0);
        return new Cursor.CursorAsIterator<>(i < 0
                ? Cursor.empty()
                : new CursorImpl(this, i)
        );
    }
    public Iterator<Integer> iterator(final int index) {
        Objects.checkIndex(index, this.memory.get().length());
        return new Cursor.CursorAsIterator<>(
                new CursorImpl(this, index)
        );
    }

    private int nextSetBit(final int fromIndex) {
        int u = cellIndex(fromIndex);
        final BitwiseModifiableMemory<Long> mem = this.memory.get();
        if (u >= mem.length()) {
            throw new IndexOutOfBoundsException();
        }
        for (long word = mem.fetch(u) & (-1L << fromIndex);;) {
            if (word != 0) {
                return (u * BITS_PER_CELL) + Long.numberOfTrailingZeros(word);
            } else if (++u >= mem.length()) {
                return -1;
            }
            word = mem.fetch(u);
        }
    }

    @Override
    public int hashCode() {
        final BitwiseModifiableMemory<Long> mem = this.memory.get();
        long h = 1234;
        for (int i = mem.length(); --i >= 0; ) {
            h ^= mem.fetch(i) * (i + 1);
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
