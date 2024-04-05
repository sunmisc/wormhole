package sunmisc.utils.concurrent.memory;

import sunmisc.utils.concurrent.UnblockingArrayBuffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;

import static java.lang.Integer.numberOfLeadingZeros;


/**
 * This implementation is based on segments
 * each segment grows by a power of two
 * this can be considered as a geometric progression
 * <p>2 * (2^n - 1)
 * <p>The maximum size is 2^30, this is equivalent to the maximum capacity of {@link java.util.concurrent.ConcurrentHashMap}, but you can increase the maximum if desired
 * <p>This class is well suited for implementing data structures such as HashMap, ArrayList...
 * <p>It is also completely thread safe. Operations such as: set, get, nextGrow are performed in O(1)
 * <p>This implementation does not replace {@link UnblockingArrayBuffer}, since it physically does not copy anything, this may be unacceptable:<ul>
 * <li>if we need an array of its own length (not a power of two)
 * <li>severe memory limitations
 * <li>it is necessary to reduce the size (can be implemented)
 * </ul>
 * The current implementation is small, but may take up a little more memory than {@link UnblockingArrayBuffer}
 * <p>This class was primarily created for data structures that only increase:
 * hashmaps, queues, lists, sets, etc.
 *
 * @author Sunmisc Unsafe
 * @param <U> The base class of elements held in this array
 */
@SuppressWarnings("unchecked")
public class ReferenceSegmentMemory<U>
        implements ModifiableMemory<U>, RandomAccess {
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private final Segment<U>[] segments = new Segment[30]; {
        segments[0] = newSegment(2);
    }
    private volatile int ctl = 2;

    // todo: can pass function and inverse function (derivative) for internal mechanics
    public ReferenceSegmentMemory() { }

    public ReferenceSegmentMemory(int initialCapacity) {
        realloc(initialCapacity);
    }

    private static <E> int
    indexForSegment(final Segment<E> segment, final int index) {
        return index < 2 ? index : index - segment.length();
    }

    private static int segmentForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }

    @Override
    public Optional<U> fetch(int index) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<U> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return Optional.ofNullable(segment.arrayAt(i));
    }

    @Override
    public void store(int index, U e) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<U> segment = segmentAt(exponent);

        int i = indexForSegment(segment, index);
        segment.setAt(i, e);
    }

    @Override
    public U compareAndExchange(int index, U expected, U value) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<U> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.cae(i, expected, value);
    }

    @Override
    public U fetchAndStore(int index, U e) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<U> segment = segments[exponent];

        int i = indexForSegment(segment, index);
        return segment.getAndSet(i, e);
    }

    @Override
    public int length() {
        return (int) CTL.getAcquire(this);
    }

    @Override
    public void realloc(int size) {
        size = Math.max(2, size);
        int n = (-1 >>> Integer.numberOfLeadingZeros(size - 1)) + 1;
        if (n < 0 || n >= MAXIMUM_CAPACITY)
            throw new OutOfMemoryError("Required array size too large");
        int c;;
        while ((c = ctl) != n) {
            if (c > n) {
                int index = segmentForIndex(c - 1);
                Segment<U> segment = segments[index];
                if (segment != null &&
                        CTL.weakCompareAndSet(this, c, c >> 1))
                    // casSegmentAt(r, segment, null);
                    freeAt(index);
            } else {
                int index = segmentForIndex(c);
                var h = newSegment(1 << index);
                if (casSegmentAt(index, null, h)) {
                    int k = (int) CTL.compareAndExchange(this, c, c << 1);
                    if (k < c)
                        casSegmentAt(index, h, null);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("\n");
        for (int i = 0, n = segments.length; i < n; ++i) {
            Segment<U> segment = segmentAt(i);
            if (segment == null) break;
            joiner.add(segment.toString());
        }
        return joiner.toString();
    }


    private Segment<U> newSegment(int len) {
        return new Segment<>(
                (U[])new Object[len]
        );
    }

    private void freeAt(int i) {
        SEGMENTS.setRelease(segments, i, null);
    }

    private Segment<U> segmentAt(int i) {
        return (Segment<U>) SEGMENTS.getAcquire(segments, i);
    }
    private boolean
    casSegmentAt(int i, Segment<U> expected, Segment<U> segment) {
        return SEGMENTS.compareAndSet(segments, i, expected, segment);
    }

    private record Segment<E>(E[] array) {
        int length() {
            return array.length;
        }

        E arrayAt(int index) {
            return (E) AA.getAcquire(array, index);
        }

        void setAt(int index, E value) {
            AA.setRelease(array, index, value);
        }

        E getAndSet(int index, E value) {
            return (E) AA.getAndSet(array, index, value);
        }

        E cae(int i, E expected, E value) {
            return (E) AA.compareAndExchange(array, i, expected, value);
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(
                    ", ", "[", "]");
            for (int i = 0, n = length(); i < n; ++i)
                joiner.add(Objects.toString(arrayAt(i)));
            return joiner.toString();
        }

        // VarHandle mechanics
        private static final VarHandle AA
                = MethodHandles.arrayElementVarHandle(Object[].class);
    }

    // VarHandle mechanics
    private static final VarHandle SEGMENTS
            = MethodHandles.arrayElementVarHandle(Segment[].class);
    private static final VarHandle CTL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CTL = l.findVarHandle(ReferenceSegmentMemory.class,
                    "ctl", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}