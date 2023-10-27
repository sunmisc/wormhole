package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;


/**
 * This implementation is based on segments
 * each segment grows by a power of two
 * this can be considered as a geometric progression
 * <p>2 * (2^n - 1)
 * <p>The maximum size is 2^30, this is equivalent to the maximum capacity of {@link java.util.concurrent.ConcurrentHashMap}, but you can increase the maximum if desired
 * <p>This class is well suited for implementing data structures such as HashMap, ArrayList...
 * <p>It is also completely thread safe. Operations such as: set, get, nextGrow are performed in O(1)
 * <p>All operations are performed in O(1)
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
 * @param <E> The base class of elements held in this array
 */
@SuppressWarnings("unchecked")
public class ConcurrentSegmentBuffers<E> {
    private static final int MAXIMUM_CAPACITY  = 1 << 30;
    private final Segment<E>[] segments = new Segment[30];
    private volatile int cursor;


    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ",
                "[", "]");
        forEach(e -> joiner.add(Objects.toString(e)));
        return joiner.toString();
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        for (int x = 0, n = segments.length; x < n; ++x) {
            Segment<E> segment = segmentAt(x);

            if (segment == null) return;

            for (int y = 0, h = segment.length(); y < h; ++y)
                action.accept(segment.arrayAt(y));
        }
    }

    public E get(int index) {
        int exponent = segmentForIndex(index);
        Segment<E> segment = segmentAt(exponent);

        if (segment == null)
            throw new IndexOutOfBoundsException();

        int i = indexForSegment(segment, index);
        return segment.arrayAt(i);
    }

    public E compareAndExchange(int index, E expected, E value) {

        int exponent = segmentForIndex(index);
        Segment<E> segment = segmentAt(exponent);
        if (segment == null)
            throw new IndexOutOfBoundsException();
        int i = indexForSegment(segment, index);
        return segment.cae(i, expected, value);
    }

    public E set(int index, E e) {

        int exponent = segmentForIndex(index);

        Segment<E> segment = segmentAt(exponent);
        if (segment == null)
            throw new IndexOutOfBoundsException();
        int i = indexForSegment(segment, index);
        return segment.setAt(i, e);
    }

    public int size() {
        return size(cursor);
    }
    private static <E> int
    indexForSegment(Segment<E> segment, int index) {
        int h = size(segment.segmentIndex());
        return index - (h - segment.length());
    }

    private static int segmentForIndex(int index) {
        return 31 - Integer.numberOfLeadingZeros(index + 2) - 1;
    }

    public int expand() {
        int x = (int) CURSOR.getAndAdd(this, 1);

        int newLen = 1 << (x + 1);

        E[] array = (E[]) new Object[newLen];

        setSegmentAt(x, new Segment<>(array));

        return newLen;
    }
    public int reduce() {
        int x = (int) CURSOR.getAndAdd(this, -1);

        setSegmentAt(x - 1, null);

        return 1 << x >> 1;
    }

    public static int maximumCapacity() {
        return MAXIMUM_CAPACITY;
    }

    private Segment<E> segmentAt(int i) {
        return (Segment<E>) AA.getAcquire(segments, i);
    }
    private void setSegmentAt(int i, Segment<E> segment) {
        AA.setRelease(segments, i, segment);
    }
    private boolean casSegmentAt(int i,
                                 Segment<E> expected,
                                 Segment<E> segment) {
        return AA.compareAndSet(segments, i, expected, segment);
    }
    private static int size(int bucket) {
        // sum g.progression
        // 2 * (2^n - 1)
        int p = bucket;
        p = 1 << p;
        p -= 1;
        p <<= 1;
        return p;
    }
    private record Segment<E>(E[] array) {
        int length() {
            return array.length;
        }
        int segmentIndex() {
            return 31 - Integer.numberOfLeadingZeros(length());
        }
        E arrayAt(int index) {
            return (E) AA.getAcquire(array, index);
        }
        E setAt(int index, E value) {
            return (E) AA.getAndSet(array, index, value);
        }
        E cae(int i, E expected, E value) {
            return (E)AA.compareAndExchange(array, i, expected, value);
        }
    }


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle CURSOR;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CURSOR = l.findVarHandle(ConcurrentSegmentBuffers.class,
                    "cursor", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
