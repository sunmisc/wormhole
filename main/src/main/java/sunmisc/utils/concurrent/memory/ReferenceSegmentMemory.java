package sunmisc.utils.concurrent.memory;

import sunmisc.utils.concurrent.UnblockingArrayBuffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

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
 * @param <E> The base class of elements held in this array
 */
@SuppressWarnings("unchecked")
public final class ReferenceSegmentMemory<E> implements ModifiableMemory<E> {
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private final ModifiableMemory<E>[] segments = new ModifiableMemory[30];
    private final AtomicInteger ctl = new AtomicInteger(1);

    // log2
    private static int segmentForIndex(final int index) {
        return 31 - numberOfLeadingZeros(index);
    }

    @Override
    public E fetch(final int index) {
        final int shift = index + 1;
        Objects.checkIndex(shift, this.ctl.get());
        final int exponent = segmentForIndex(shift);
        final ModifiableMemory<E> segment = segmentAt(exponent);
        final int i = shift - segment.length();
        return segment.fetch(i);
    }

    @Override
    public void store(final int index, final E e) {
        final int shift = index + 1;
        Objects.checkIndex(shift, this.ctl.get());
        final int exponent = segmentForIndex(shift);
        final ModifiableMemory<E> segment = segmentAt(exponent);
        final int i = shift - segment.length();
        segment.store(i, e);
    }

    @Override
    public E compareAndExchange(final int index, final E expected, final E value) {
        final int shift = index + 1;
        Objects.checkIndex(shift, this.ctl.get());
        final int exponent = segmentForIndex(shift);
        final ModifiableMemory<E> segment = segmentAt(exponent);
        final int i = shift - segment.length();
        return segment.compareAndExchange(i, expected, value);
    }

    @Override
    public E fetchAndStore(int index, final E e) {
        final int shift = index + 1;
        Objects.checkIndex(shift, this.ctl.get());
        final int exponent = segmentForIndex(shift);
        final ModifiableMemory<E> segment = segmentAt(exponent);
        final int i = shift - segment.length();
        return segment.fetchAndStore(i, e);
    }

    @Override
    public int length() {
        return this.ctl.get() - 1;
    }
    @Override
    public ModifiableMemory<E> realloc(final int size) {
        final int n = (-1 >>> Integer.numberOfLeadingZeros(size)) + 1;
        if (n < 0 || n >= MAXIMUM_CAPACITY) {
            throw new OutOfMemoryError("Required array size too large");
        }
        for (int c; (c = this.ctl.get()) != n; ) {
            if (c > n) {
                final int index = segmentForIndex(c - 1);
                final ModifiableMemory<E> segment = segmentAt(index);
                if (segment != null && this.ctl.weakCompareAndSetVolatile(c, c >> 1)) {
                    // casSegmentAt(r, segment, null){
                    freeAt(index);
                }
            } else {
                final int index = segmentForIndex(c);
                final ArrayMemory<E> h = new ArrayMemory<>(
                        1 << index);
                if (casSegmentAt(index, null, h)) {
                    final int k = this.ctl.compareAndExchange(c, c << 1);
                    if (k < c) {
                        casSegmentAt(index, h, null);
                    }
                }
            }
        }
        return this;
    }

    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner("\n");
        for (int i = 0, n = this.segments.length; i < n; ++i) {
            final ModifiableMemory<E> segment = segmentAt(i);
            if (segment == null) {
                break;
            }
            joiner.add(segment.toString());
        }
        return joiner.toString();
    }


    private void freeAt(final int i) {
        SEGMENTS.setRelease(this.segments, i, null);
    }

    private ModifiableMemory<E> segmentAt(final int i) {
        return (ModifiableMemory<E>) SEGMENTS.getAcquire(this.segments, i);
    }
    private boolean
    casSegmentAt(final int i,
                 final ModifiableMemory<E> expected,
                 final ModifiableMemory<E> segment) {
        return SEGMENTS.compareAndSet(this.segments, i, expected, segment);
    }

    // VarHandle mechanics
    private static final VarHandle SEGMENTS
            = MethodHandles.arrayElementVarHandle(ModifiableMemory[].class);
}