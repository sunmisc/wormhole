package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.StringJoiner;

import static java.lang.Integer.numberOfLeadingZeros;

@SuppressWarnings("unchecked")
public class ImmutableSegmentsMemory<E> implements ModifiableMemory<E> {
    private final Object[][] segments;

    public ImmutableSegmentsMemory(final int size) {
        this(make(size));
    }
    private ImmutableSegmentsMemory(final Object[][] segments) {
        this.segments = segments;
    }

    // O(30)
    @Override
    public ImmutableSegmentsMemory<E> realloc(int size) {
        size = 32 - numberOfLeadingZeros(size);
        final Object[][] prev = this.segments;
        int p = prev.length - 1;
        final Object[][] copy = Arrays.copyOf(prev, size);
        for (; p < size; ++p) {
            copy[p] = new Object[1 << p];
        }
        return new ImmutableSegmentsMemory<>(copy);
    }
    @Override
    public int length() {
        return 1 << this.segments.length;
    }

    @Override
    public E fetch(final int index) {
        final int exponent = segmentForIndex(index);
        final Object[] segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        return (E) AA.getAcquire(segment, i);
    }

    @Override
    public E fetchAndStore(
            final int index, final E value
    ) throws IndexOutOfBoundsException {
        final int exponent = segmentForIndex(index);
        final Object[] segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        return (E) AA.getAndSet(segment, i, value);
    }

    @Override
    public E
    compareAndExchange(
            final int index, final E expected, final E newValue
    ) throws IndexOutOfBoundsException {
        final int exponent = segmentForIndex(index);
        final Object[] segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        return (E) AA.compareAndExchange(segment, i, expected, newValue);
    }

    @Override
    public void store(final int index, final E val) {
        final int exponent = segmentForIndex(index);
        final Object[] segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        AA.setRelease(segment, i, val);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("\n");
        for (final Object[] segment : this.segments) {
            final StringJoiner joiner = new StringJoiner(
                    ", ", "[", "]");
            for (int i = 0, n = segment.length; i < n; ++i) {
                joiner.add(String.valueOf(
                        AA.getAcquire(segment, i))
                );
            }
            builder.append(joiner);
        }
        return builder.toString();
    }
    // log2
    private static int segmentForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }
    private static int indexForSegment(final Object[] segment, final int index) {
        return index < 2 ? index : index - segment.length;
    }
    @SuppressWarnings("unchecked")
    private static <E> E[][] make(int size) {
        size = 32 - numberOfLeadingZeros(Math.max(size - 1, 1));
        final Object[][] alloc = new Object[size][];
        alloc[0] = new Object[2];
        for (int k = 1; k < size; ++k) {
            alloc[k] = new Object[1 << k];
        }
        return (E[][]) alloc;
    }


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
}
