package sunmisc.utils.concurrent;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * An array that supports full concurrency retrievals
 * and high expected update concurrency.
 * This array is based entirely on the free-lock mechanism.
 *
 * @author Sunmisc Unsafe
 * @param <E> The base class of elements held in this array
 */
public class UnblockingArrayBuffer<E>
        extends ConcurrentIndexMap<E>
        implements Serializable {
    /*
     * Overview:
     *
     * The purpose of this array is not only to quickly read/write
     * to different slots of the array, but also
     * to atomically change the array with possible parallelization
     *
     * An array can be parallelized if another thread arrives
     * at the moment of transferring elements from one array to
     * another marking the moved slots in the old array as ForwardingPointer,
     * we update the values through a special object that stores
     * a volatile value field, this is necessary in order
     * to read the object without blocking
     *
     * At the moment when moving from one array to another,
     * we divide the array into chunks, each thread works with its own chunk
     * but at the same time, we can forget about the new array,
     * if it is found that a new array has appeared,
     * it depends on the sizes of the chunks themselves
     *
     * For this we have strideIndex and pendingCount field strideIndex
     * divides the array into chunks and gives to the streams,
     * and pendingCount is the total number of chunks that are already filled
     *
     * the memory problem is solved by a partial spin lock,
     * in cases of deleting an element, for this we use a redirect node,
     * which allows us to read/update the value,
     * but does not allow us to remove the node from the array,
     * this is a unique case of deletion and resize
     *
     * The transfer itself is in the "loop"
     * after a successful commit of the new array
     * we start migrating the current or more recent ForwardingPointer
     * before a successful commit
     * At the same time, readers do not see the new array until the commit,
     *  so there is no disagreement with the size
     */

    /* ---------------- Constants -------------- */

    @Serial
    private static final long serialVersionUID = -1151544938255125591L;

    /**
     * Number of CPUS, to place bounds on some sizing's
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * The minimum number of beginnings per transfer step
     * Ranges are subdivided to allow multiple resizing threads
     */
    static final int MIN_TRANSFER_STRIDE = 16;

    /* ---------------- Field -------------- */
    transient volatile Shared shared; // current array claimant

    transient EntrySetView<E> entrySet;


    public UnblockingArrayBuffer(int size) {
        this.shared = new QShared(new Object[size]);
    }
    public UnblockingArrayBuffer(E[] array) {
        int n; Object o;
        // parallelize copy using Stream API?
        Object[] nodes = new Object[n = array.length];
        for (int i = 0; i < n; i++) {
            if ((o = array[i]) != null)
                nodes[i] = new QCell<>(o);
        }
        this.shared = new QShared(nodes);
    }


    /**
     * @return the current length of the array
     */
    @Override
    public int size() {
        return shared.array().length;
    }

    /**
     * Returns the current value of the element at index {@code i}
     *
     * @param c the index
     * @return the current value
     */
    @Override
    @SuppressWarnings("unchecked")
    public E get(Object c) {
        Objects.requireNonNull(c);
        int i = (int) c;
        Object[] arr = shared.array();
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null)
                return null;
            else if (o instanceof ForwardingPointer t) {
                arr = t.nextCells;
                Objects.checkIndex(i, arr.length);
            } else if (o instanceof Cell<?> f)
                return (E) f.getValue();
        }
    }

    /**
     * Sets the element at index {@code i} to {@code newValue}
     *
     * @param i the index
     * @param newValue the new value
     * @return the previous value
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public E put(Integer i, E newValue) {
        Objects.requireNonNull(i);
        Objects.requireNonNull(newValue);
        Object[] arr = shared.array();
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null) {
                if (weakCasAt(arr, i, null,
                        new QCell<>(newValue)))
                    return null;
            }
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell n) {
                Object val = n.getValue();
                // Replacing a dead cell
                if (val == null) {
                    if (weakCasAt(arr, i, n, new QCell<>(newValue)))
                        return null;
                } else if (n.cas(val, newValue))
                    return (E) val;
            }
        }
    }

    /**
     * Remove a cell from the index {@code i}
     *
     * @param c the index
     * @return the previous value
     */
    @Override
    @SuppressWarnings("unchecked")
    public E remove(Object c) {
        Objects.requireNonNull(c);
        int i = (int)c;
        Object[] arr = shared.array();
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null)
                return null;
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof ForwardingCell)
                Thread.onSpinWait();
            else if (o instanceof Cell<?> n &&
                    weakCasAt(arr, i, o, null))
                return (E) n.getValue();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E putIfAbsent(@NotNull Integer i, E val) {
        Object[] arr = shared.array();
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null) {
                if (weakCasAt(arr, i, null,
                        new QCell<>(val)))
                    return null;
            }
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell<?> n) {
                Object v = n.getValue();
                if (v == null) {
                    if (weakCasAt(arr, i, n, new QCell<>(val)))
                        return null;
                } else
                    return (E) v;
            }
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean replace(@NotNull Integer i,
                           @NotNull E oldVal, @NotNull E newVal) {
        Object[] arr = shared.array();
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null)
                return false;
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell n)
                return n.cas(oldVal, newVal);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean remove(@NotNull Object idx, Object oldVal) {
        if (oldVal == null) throw new NullPointerException();
        int i = (int) idx;
        Object[] arr = shared.array();
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null)
                return false;
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell n) {
                Object val = n.getValue();
                /*
                 * marks a dead cell; Dead cell - null value
                 * after that, we only have to change the cell,
                 * this will allow us to safely remove dead cells,
                 * without fear that we will remove the added value
                 */
                if (val == null || !n.cas(oldVal, null))
                    return false;
                for (;;) {
                    if ((o = arrayAt(arr, i)) == null)
                        return false;
                    else if (o instanceof ForwardingPointer f)
                        arr = helpTransfer(f, i);
                    else if (o instanceof ForwardingCell<?>)
                        Thread.onSpinWait();
                    else if (o instanceof Cell<?> p) {
                        if (n != p)
                            return false;
                        else if (weakCasAt(arr, i, p, null))
                            return true;
                    }
                }
            }
        }
    }
    /**
     * resize array to {@code i} length
     * @param operator new array length
     */
    @Override
    public void resize(IntUnaryOperator operator) {
        boolean advance = false;
        for (Shared p;;) {
            int len;
            if (((p = shared) instanceof QShared) &&
                    (len = p.numberOfTransfers()) == operator.applyAsInt(len))
                return;
            else if (advance) {
                // after a successful commit, you can be sure that
                // we will commit either the same array or the next one
                if (p instanceof ForwardingPointer f &&
                        tryTransfer(f) instanceof ForwardingPointer r && // recheck
                        !LEVELS.weakCompareAndSet(
                                this, r,
                                new QShared((r.nextCells))))
                    continue;
                return;
            } else {
                int nextLen = operator.applyAsInt(len = p.capacity());
                if (len == nextLen ||
                        LEVELS.weakCompareAndSet(
                                this, p,
                                new ForwardingPointer(p, new Object[nextLen])))
                    advance = true;
            }
        }
    }
    private Object[]
    helpTransfer(ForwardingPointer a, int targetIndex) {
        Shared p = tryTransfer(a);

        Object[] array = p instanceof ForwardingPointer f
                ? f.nextCells : p.array();
        Objects.checkIndex(targetIndex, array.length);

        return array;
    }
    private Shared tryTransfer(ForwardingPointer a) {
        int startChunk;
        outer: while ((startChunk = a.strideIndex) < a.bound) {
            int endChunk = Math.min(a.bound, startChunk + a.stride);
            if (!a.weakCasStride(startChunk, endChunk))
                continue;
            for (int i = startChunk; i < endChunk; i++) {
                Shared n = shared;
                if (n == a)
                    a.transferSlot(i);
                else if (n instanceof ForwardingPointer f) {
                    // trying to switch to a newer array
                    a = f;
                    continue outer;
                }
                else
                    return n; // our mission is over
            }
            final int delta = endChunk - startChunk,
                    f = a.bound;
            /*
             * after a successful cas, we can read either
             * the set value or a value greater than the set value,
             * which is already out of scope of the array
             */
            if (delta > 0 &&
                    a.getAndAddPendingCount(delta) + delta >= f)
                return a;
            else if (endChunk >= f)
                break;
        }
        // recheck before commit and help
        for (int i = 0; i < startChunk; i++) {
            if (a.pendingCount >= startChunk)
                return a;
            a.transferSlot(i);
        }
        // non-volatile write
        a.pendingCount = a.bound;
        return a;
    }

    static final class ForwardingPointer implements Shared {
        final int bound; // last index of elements from old to new
        final int stride; // the size of the transfer chunk can be from 1 to fence
        final Object[] prevCells, nextCells; // owning array

        int strideIndex; // current transfer chunk
        int pendingCount; // total number of transferred chunks

        ForwardingPointer(Shared prevShared, Object[] nextArray) {
            this.prevCells = prevShared.array(); this.nextCells = nextArray;
            // calculate the last index
            int n = Math.min(prevShared.numberOfTransfers(), nextArray.length);
            this.bound = n;
            // threshold
            this.stride = Math.max(MIN_TRANSFER_STRIDE, (n >>> 3) / NCPU);
        }
        @Override public int capacity() { return nextCells.length; }

        @Override public Object[] array() { return prevCells; }

        @Override public int numberOfTransfers() { return bound; }

        int getAndAddPendingCount(int v) {
            return (int) PENDINGCOUNT.getAndAdd(this, v);
        }
        boolean weakCasStride(int c, int v) {
            return STRIDEINDEX.weakCompareAndSet(this, c, v);
        }
        void transferSlot(int i) {
            for (Object[] sh = prevCells;;) {
                Object o;
                if ((o = arrayAt(sh, i)) == this ||
                        (o == null &&
                        (o = caeAt(sh, i, null, this)) == null ||
                        o == this))
                    break;
                else if (o instanceof ForwardingPointer f)
                    sh = f.nextCells;
                else if (o instanceof ForwardingCell) {
                    // Thread.onSpinWait();
                }
                else if (o instanceof Cell<?> e) {
                    Object c;
                    if ((c = caeAt(sh, i,
                            o, new ForwardingCell<>(e))) == o) {
                        // assert nextCells[i] == null;
                        nextCells[i] = o;
                        // StoreStore fence
                        setAt(sh, i, this);
                        break;
                    } else if (c == this)
                        break;
                }
            }
        }

        // VarHandle mechanics
        private static final VarHandle STRIDEINDEX;
        private static final VarHandle PENDINGCOUNT;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                STRIDEINDEX = l.findVarHandle(ForwardingPointer.class, "strideIndex", int.class);
                PENDINGCOUNT = l.findVarHandle(ForwardingPointer.class, "pendingCount", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    @NotNull
    @Override
    public Set<Map.Entry<Integer,E>> entrySet() {
        EntrySetView<E> es;
        if ((es = entrySet) != null) return es;
        return entrySet = new EntrySetView<>(this);
    }

    static final class EntrySetView<E> extends AbstractSet<Map.Entry<Integer,E>> {
        final UnblockingArrayBuffer<E> array;
        EntrySetView(UnblockingArrayBuffer<E> array) {
            this.array = array;
        }
        @Override
        public @NotNull Iterator<Entry<Integer, E>> iterator() {
            return new EntrySetItr<>(array);
        }

        @Override public int size() { return array.size(); }
    }
    static final class EntrySetItr<E> implements Iterator<Map.Entry<Integer,E>> {
        final UnblockingArrayBuffer<E> array;
        int cursor = -1;
        E next;

        EntrySetItr(UnblockingArrayBuffer<E> array) {
            this.array = array;
        }
        @Override
        @SuppressWarnings("unchecked")
        public boolean hasNext() {
            Object[] arr = array.shared.array();
            int i = ++cursor;
            if (i == arr.length) {
                cursor = -1; // next = null;?
                return false;
            }
            for (Object o; ; ) {
                if ((o = arrayAt(arr, i)) == null) {
                    next = null;
                    return true;
                } else if (o instanceof ForwardingPointer t) {
                    arr = t.nextCells;
                    if (i == arr.length) {
                        cursor = -1; // next = null;?
                        return false;
                    }
                } else if (o instanceof Cell<?> f) {
                    next = (E) f.getValue();
                    return true;
                }
            }
        }

        @Override
        public Map.Entry<Integer,E> next() {
            int k = cursor;
            if (k >= 0)
                return new IndexEntry<>(k, next);
            throw new NoSuchElementException();
        }
        @Override
        public void remove() {
            final int c = cursor;
            if (c < 0)
                throw new IllegalStateException();
            array.remove(c);
            next = null;
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream s)
            throws IOException {
        forEach((k,v) -> {
            try {
                s.writeObject(k); s.writeObject(v);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        s.writeObject(null);
    }
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        List<Object> list = new ArrayList<>();
        for (Object k,v;;) {
            k = s.readObject();
            if (k == null)
                break;
            v = s.readObject();
            list.add((int) k,v);
        }
        this.shared = new QShared(list.toArray());
    }
    /*
     * Atomic access methods are used for array elements as well
     * as elements of in-progress next array while resizing
     */
    static Object arrayAt(Object[] arr, int i) {
        return AA.getAcquire(arr, i);
    }
    static void setAt(Object[] arr, int i, Object v) {
        AA.setRelease(arr,i,v);
    }
    static boolean weakCasAt(Object[] arr, int i, Object c, Object v) {
        return AA.weakCompareAndSet(arr,i,c,v);
    }
    static Object caeAt(Object[] arr, int i, Object c, Object v) {
        return AA.compareAndExchange(arr,i,c,v);
    }

    @FunctionalInterface
    interface Shared {
        Object[] array();

        default int numberOfTransfers() {
            return array().length;
        }
        default int capacity() {
            return array().length;
        }
    }

    interface Cell<E> {
        E getValue();

        boolean cas(E c, E v);
    }

    record ForwardingCell<E>(Cell<E> main) implements Cell<E> {
        @Override public E getValue() { return main.getValue(); }
        @Override public boolean cas(E c, E v) { return main.cas(c,v); }
        @Override public String toString() { return main.toString(); }
    }
    static final class QCell<E> implements Cell<E> {
        volatile E val;
        QCell(E val) { this.val = val; }

        @Override
        public E getValue() {
            return val;
        }

        @Override
        public boolean cas(E c, E v) {
            return VAL.compareAndSet(this, c,v);
        }

        @Override
        public String toString() {
            return Objects.toString(val);
        }

        // VarHandle mechanics
        private static final VarHandle VAL;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VAL = l.findVarHandle(QCell.class, "val", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }


    record QShared(Object[] array) implements Shared {} // inline type


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle LEVELS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            LEVELS = l.findVarHandle(UnblockingArrayBuffer.class, "shared", Shared.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}