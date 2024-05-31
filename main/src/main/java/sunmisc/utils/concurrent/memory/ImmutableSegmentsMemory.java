package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.StringJoiner;

import static java.lang.Integer.numberOfLeadingZeros;

@SuppressWarnings("unchecked")
public class ImmutableSegmentsMemory<E> implements ModifiableMemory<E> {
    private final Object[][] segments;

    public ImmutableSegmentsMemory(int size) {
        this(make(size));
    }
    private ImmutableSegmentsMemory(Object[][] segments) {
        this.segments = segments;
    }

    // O(30)
    @Override
    public ImmutableSegmentsMemory<E> realloc(int size) {
        size = 32 - numberOfLeadingZeros(size);
        final Object[][] prev = segments;
        final int p = prev.length;
        final Object[][] copy = Arrays.copyOf(prev, size);
        for (int k = p - 1; k < size; ++k)
            copy[k] = new Object[1 << k];
        return new ImmutableSegmentsMemory<>(copy);
    }
    @Override
    public int length() {
        return 1 << segments.length;
    }

    @Override
    public E fetch(int index) {
        final int exponent = segmentForIndex(++index);
        final Object[] segment = segments[exponent];
        final int i = index - segment.length;
        return (E) AA.getAcquire(segment, i);
    }

    @Override
    public E fetchAndStore(
            int index, E value
    ) throws IndexOutOfBoundsException {
        final int exponent = segmentForIndex(++index);
        final Object[] segment = segments[exponent];
        final int i = index - segment.length;
        return (E) AA.getAndSet(segment, i, value);
    }

    @Override
    public E
    compareAndExchange(
            int index, E expected, E newValue
    ) throws IndexOutOfBoundsException {
        final int exponent = segmentForIndex(++index);
        final Object[] segment = segments[exponent];
        final int i = index - segment.length;
        return (E) AA.compareAndExchange(segment, i, expected, newValue);
    }

    @Override
    public void store(int index, E val) {
        final int exponent = segmentForIndex(++index);
        final Object[] segment = segments[exponent];
        final int i = index - segment.length;

        AA.setRelease(segment, i, val);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("\n");

        for (Object[] segment : segments) {

            StringJoiner joiner = new StringJoiner(
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
        return 31 - numberOfLeadingZeros(index);
    }
    @SuppressWarnings("unchecked")
    private static <E> E[][] make(int size) {
        size = 32 - numberOfLeadingZeros(size);
        Object[][] alloc = new Object[size][];
        for (int k = 0; k < size; ++k)
            alloc[k] = new Object[1 << k];
        return (E[][]) alloc;
    }


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
}
