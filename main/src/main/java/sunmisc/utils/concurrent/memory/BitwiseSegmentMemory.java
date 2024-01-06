package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;

import static java.lang.Integer.numberOfLeadingZeros;

@SuppressWarnings("unchecked")
public class BitwiseSegmentMemory<E extends Number>
        implements BitwiseModifiableMemory<E> {

    private static final int MAXIMUM_CAPACITY = 1 << 30;

    // VarHandle mechanics
    private static final VarHandle
            LONGS    = MethodHandles.arrayElementVarHandle(long[].class),
            INTEGERS = MethodHandles.arrayElementVarHandle(int[].class),
            SHORTS   = MethodHandles.arrayElementVarHandle(short[].class),
            BYTES    = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle CTL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CTL = l.findVarHandle(BitwiseSegmentMemory.class,
                    "ctl", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Segment<E>[] segments;
    private volatile int ctl = 2;
    private final Function<Integer, Segment<E>> mapping;

    public BitwiseSegmentMemory(Class<E> componentType) {
        Function<Integer, Segment<E>> map;
        if (componentType == byte.class ||
                componentType == Byte.class)
            map = length -> new Segment<>(new byte[length], BYTES);
        else if (componentType == short.class ||
                componentType == Short.class)
            map = length -> new Segment<>(new short[length], SHORTS);
        else if (componentType == int.class ||
                componentType == Integer.class)
            map = length -> new Segment<>(new int[length], INTEGERS);
        else if (componentType == long.class ||
                componentType == Long.class)
            map = length -> new Segment<>(new long[length], LONGS);
        else
            throw new IllegalArgumentException("Component type is not bitwise");
        Segment<E>[] segments = new Segment[30];
        segments[0] = map.apply(2);
        this.segments = segments;
        this.mapping = map;
    }

    private static <E> int
    indexForSegment(final Segment<E> segment, final int index) {
        return index < 2 ? index : index - segment.length();
    }

    private static int segmentForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }

    private record Segment<E>(Object array, VarHandle handle) {

        int length() {
            return Array.getLength(array);
        }
        E arrayAt(int index) {
            return (E) handle.getAcquire(array, index);
        }

        void setAt(int index, E value) {
            handle.setRelease(array, index, value);
        }

        E getAndSet(int index, E value) {
            return (E) handle.getAndSet(array, index, value);
        }

        E cae(int i, E expected, E value) {
            return (E) handle.compareAndExchange(array, i, expected, value);
        }

        E getAndBitwiseOr(int index, E mask) {
            return (E) handle.getAndBitwiseOr(array, index, mask);
        }

        E getAndBitwiseAnd(int index, E mask) {
            return (E) handle.getAndBitwiseAnd(array, index, mask);
        }

        E getAndBitwiseXor(int index, E mask) {
            return (E) handle.getAndBitwiseXor(array, index, mask);
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(
                    ", ", "[", "]");
            for (int i = 0, n = length(); i < n; ++i)
                joiner.add(Objects.toString(arrayAt(i)));
            return joiner.toString();
        }
    }



    @Override
    public void realloc(int size) {
        size = Math.max(2, size);
        int n = -1 >>> Integer.numberOfLeadingZeros(size - 1);
        if (n < 0 || n >= MAXIMUM_CAPACITY)
            throw new OutOfMemoryError("Required array size too large");
        int c; ++n;
        while ((c = ctl) != n) {
            if (c > n) {
                int index = segmentForIndex(c - 1);
                Segment<E> segment = segments[index];
                if (segment != null &&
                        CTL.weakCompareAndSet(this, c, c >> 1))
                    // casSegmentAt(r, segment, null);
                    freeSegment(index);
            } else {
                int index = segmentForIndex(c);
                var h = mapping.apply(1 << index);
                if (casSegmentAt(index, null, h)) {
                    int k = (int) CTL.compareAndExchange(this, c, c << 1);
                    if (k < c)
                        casSegmentAt(index, h, null);
                }
            }
        }
    }

    @Override
    public E get(int index) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<E> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.arrayAt(i);
    }

    @Override
    public E compareAndExchange(int index, E expected, E value) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<E> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.cae(i, expected, value);
    }

    @Override
    public void store(int index, E e) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<E> segment = segmentAt(exponent);

        int i = indexForSegment(segment, index);
        segment.setAt(i, e);
    }

    @Override
    public E getAndSet(int index, E e) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<E> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.getAndSet(i, e);
    }

    @Override
    public E getAndBitwiseOr(int index, E mask) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<E> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.getAndBitwiseOr(i, mask);
    }

    @Override
    public E getAndBitwiseAnd(int index, E mask) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<E> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.getAndBitwiseAnd(i, mask);
    }

    @Override
    public E getAndBitwiseXor(int index, E mask) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<E> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.getAndBitwiseXor(i, mask);
    }

    @Override
    public int length() {
        return (int) CTL.getAcquire(this);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("\n");
        for (Segment<E> segment : segments) {
            if (segment == null) break;
            joiner.add(segment.toString());
        }
        return joiner.toString();
    }


    private void freeSegment(int i) {
        AA.setRelease(segments, i, null);
    }

    private Segment<E> segmentAt(int i) {
        return (Segment<E>) AA.getAcquire(segments, i);
    }
    private boolean
    casSegmentAt(int i, Segment<E> expected, Segment<E> segment) {
        return AA.compareAndSet(segments, i, expected, segment);
    }
}
