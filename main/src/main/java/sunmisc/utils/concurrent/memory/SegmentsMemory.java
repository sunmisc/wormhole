package sunmisc.utils.concurrent.memory;

import java.util.Arrays;

import static java.lang.Integer.numberOfLeadingZeros;

public final class SegmentsMemory<E> implements ModifiableMemory<E> {
    private final ModifiableMemory<E>[] segments;

    public SegmentsMemory(final int size) {
        this(make(size));
    }

    private SegmentsMemory(final ModifiableMemory<E>[] segments) {
        this.segments = segments;
    }

    // O(30)
    @Override
    public SegmentsMemory<E> realloc(final int size) {
        final int aligned = 32 - numberOfLeadingZeros(Math.max(size - 1, 1));
        final ModifiableMemory<E>[] prev = this.segments;
        final ModifiableMemory<E>[] copy = Arrays.copyOf(prev, aligned);
        for (int p = prev.length; p < aligned; ++p) {
            copy[p] = new ArrayMemory<>(1 << p);
        }
        return new SegmentsMemory<>(copy);
    }
    @Override
    public int length() {
        return 1 << this.segments.length;
    }

    @Override
    public E fetch(final int index) {
        final int exponent = segmentForIndex(index);
        final ReadableMemory<E> segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        return segment.fetch(i);
    }

    @Override
    public E fetchAndStore(final int index,
                           final E value
    ) throws IndexOutOfBoundsException {
        final int exponent = segmentForIndex(index);
        final ModifiableMemory<E> segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        return segment.fetchAndStore(i, value);
    }

    @Override
    public E compareAndExchange(final int index,
                                final E expected,
                                final E newValue
    ) throws IndexOutOfBoundsException {
        final int exponent = segmentForIndex(index);
        final ModifiableMemory<E> segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        return segment.compareAndExchange(i, expected, newValue);
    }

    @Override
    public void store(final int index, final E val) {
        final int exponent = segmentForIndex(index);
        final ModifiableMemory<E> segment = this.segments[exponent];
        final int i = indexForSegment(segment, index);
        segment.store(i, val);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("\n");
        for (final ReadableMemory<E> segment : this.segments) {
            if (segment == null) {
                break;
            }
            builder.append(segment);
        }
        return builder.toString();
    }
    // log2
    private static int segmentForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }

    private int indexForSegment(final ReadableMemory<E> segment, final int index) {
        return index < 2 ? index : index - segment.length();
    }

    private static <E> ModifiableMemory<E>[] make(final int size) {
        final int segments = 32 - numberOfLeadingZeros(Math.max(size - 1, 1));
        @SuppressWarnings("unchecked")
        final ModifiableMemory<E>[] alloc = new ModifiableMemory[segments];
        alloc[0] = new ArrayMemory<>(2);
        for (int segment = 1; segment < segments; ++segment) {
            alloc[segment] = new ArrayMemory<>(1 << segment);
        }
        return alloc;
    }
}
