package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

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
public class ConcurrentSegmentBuffers<U> {
    private static final int MAXIMUM_CAPACITY  = 1 << 30;
    private final Segment<U>[] segments = new Segment[30]; {

            segments[0] = new Segment<>((U[]) new Object[2]);
    }
    private volatile int cursor = 1;
    private volatile int size = 2; // alignment gap


    private ConcurrentSegmentBuffers() { }

    public static <U> ConcurrentSegmentBuffers<U> of(int initialCapacity) {

        initialCapacity = Math.max(2, initialCapacity);
        int u = 31 - numberOfLeadingZeros(initialCapacity - 1);

        ConcurrentSegmentBuffers<U> buffers
                = new ConcurrentSegmentBuffers<>();
        for (int i = 0; i < u; ++i)
            buffers.expand();
        return buffers;
    }

    public U get(int index) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<U> segment = segmentAt(exponent);

        int i = indexForSegment(segment, index);
        return segment.arrayAt(i);
    }

    public U compareAndExchange(int index, U expected, U value) {
        Objects.checkIndex(index, length());

        int exponent = segmentForIndex(index);
        Segment<U> segment = segmentAt(exponent);

        int i = indexForSegment(segment, index);
        return segment.cae(i, expected, value);
    }

    public void set(int index, U e) {
        Objects.checkIndex(index, length());
        int exponent = segmentForIndex(index);

        Segment<U> segment = segmentAt(exponent);

        int i = indexForSegment(segment, index);
        segment.setAt(i, e);
    }

    public U getAndSet(int index, U e) {
        Objects.checkIndex(index, length());
        int exponent = segmentForIndex(index);

        Segment<U> segment = segmentAt(exponent);

        int i = indexForSegment(segment, index);
        return segment.getAndSet(i, e);
    }

    public int length() {
        return (int) SIZE.getAcquire(this);
    }

    public void forEach(Consumer<? super U> action) {
        Objects.requireNonNull(action);
        for (int x = 0, n = segments.length; x < n; ++x) {
            Segment<U> segment = segmentAt(x);

            if (segment == null) return;

            for (int y = 0, h = segment.length(); y < h; ++y)
                action.accept(segment.arrayAt(y));
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


    private static <E> int
    indexForSegment(final Segment<E> segment, final int index) {
        return index < 2 ? index : index - segment.length();
    }

    private static int segmentForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }

    public int expand() {
        int x = (int) CURSOR.getAndAdd(this, 1);

        int newLen = 1 << x;

        U[] array = (U[]) new Object[newLen];

        if (casSegmentAt(x, null, new Segment<>(array))) {

            // sum g.progression
            // (2^n - 1)

            int u;
            do {
                u = (int) SIZE.getOpaque(this);
            } while (!SIZE.weakCompareAndSetRelease(this, u, u << 1));
        }


        return newLen;
    }
    int reduce() {
        int x = (int) CURSOR.getAndAdd(this, -1);

        setSegmentAt(x - 1, null);

        int u;
        do {
            u = (int) SIZE.getOpaque(this);
        } while (!SIZE.weakCompareAndSetRelease(this, u, u >> 1));
        return u;
    }

    public static int maximumCapacity() {
        return MAXIMUM_CAPACITY;
    }

    private Segment<U> segmentAt(int i) {
        return (Segment<U>) AA.getAcquire(segments, i);
    }
    private void setSegmentAt(int i, Segment<U> segment) {
        AA.setRelease(segments, i, segment);
    }
    private boolean
    casSegmentAt(int i, Segment<U> expected, Segment<U> segment) {
        return AA.compareAndSet(segments, i, expected, segment);
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
            return (E)AA.compareAndExchange(array, i, expected, value);
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


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle CURSOR, SIZE;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CURSOR = l.findVarHandle(ConcurrentSegmentBuffers.class,
                    "cursor", int.class);
            SIZE = l.findVarHandle(ConcurrentSegmentBuffers.class,
                    "size", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
