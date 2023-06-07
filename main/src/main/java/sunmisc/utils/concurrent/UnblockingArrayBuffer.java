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
    transient volatile ContainerBridge bridge; // current array claimant

    transient EntrySetView<E> entrySet;


    public UnblockingArrayBuffer(int size) {
        this.bridge = new ContainerBridge(new Object[size]);
    }
    public UnblockingArrayBuffer(E[] array) {
        // parallelize copy using Stream API?
        int n = array.length, i = 0;
        Object[] nodes = new Object[n];
        for (Object o; i < n; i++) {
            if ((o = array[i]) != null)
                nodes[i] = new Cell<>(o);
        }
        this.bridge = new ContainerBridge(nodes);
    }

    /**
     * @return the current length of the array
     */
    @Override
    public int size() {
        return bridge.array.length;
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
        Object[] arr = bridge.array;
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null)
                return null;
            else if (o instanceof ForwardingPointer t) {
                arr = t.nextCells;
                Objects.checkIndex(i, arr.length);
            } else if (o instanceof Cell<?> f)
                return (E) f.value;
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
        Object[] arr = bridge.array;
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null) {
                if (weakCasAt(arr, i, null,
                        new Cell<>(newValue)))
                    return null;
            }
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell n) {
                Object val = n.value;
                // Replacing a dead cell
                if (val == null) {
                    if (weakCasAt(arr, i, n, new Cell<>(newValue)))
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
        Object[] arr = bridge.array;
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null)
                return null;
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell<?> n &&
                    weakCasAt(arr, i, o, null))
                return (E) n.value;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E putIfAbsent(@NotNull Integer i, E val) {
        Object[] arr = bridge.array;
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null) {
                if (weakCasAt(arr, i, null,
                        new Cell<>(val)))
                    return null;
            }
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell<?> n) {
                Object v = n.value;
                if (v == null) {
                    if (weakCasAt(arr, i, n, new Cell<>(val)))
                        return null;
                } else
                    return (E) v;
            }
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean replace(@NotNull Integer i,
                           @NotNull E oldVal,
                           @NotNull E newVal) {
        Object[] arr = bridge.array;
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
        Objects.requireNonNull(oldVal);

        int i = (int) idx;
        Object[] arr = bridge.array;
        Objects.checkIndex(i, arr.length);
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null)
                return false;
            else if (o instanceof ForwardingPointer f)
                arr = helpTransfer(f, i);
            else if (o instanceof Cell n) {
                Object val = n.value;
                /*
                 * marks a dead cell; Dead cell - null value
                 * after that, we only have to change the cell,
                 * this will allow us to safely remove dead cells,
                 * without fear that we will remove the added value
                 */
                if (val == null || !n.cas(oldVal, null))
                    return false;

                for (o = arrayAt(arr, i);;) {
                    if (o == null)
                        return false;
                    else if (o instanceof ForwardingPointer f)
                        arr = helpTransfer(f, i);
                    else if (o instanceof Cell<?> p) {
                        if (n != p)
                            return false;
                        else if ((o = caeAt(arr, i, p, null)) == p)
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
        for (boolean resizing = false;;) {
            ContainerBridge p = bridge;
            if (resizing) {
                // after a successful commit, you can be sure that
                // we will commit either the same array or the next one
                if (p instanceof ForwardingPointer f &&
                        // recheck
                        tryTransfer(f) instanceof ForwardingPointer r &&
                        !BRIDGE.weakCompareAndSet(
                                this, r,
                                new ContainerBridge(r.nextCells)))
                    continue;
                return;
            }
            int n = p.array.length, nextSize = operator.applyAsInt(n);
            if (n == nextSize || BRIDGE.weakCompareAndSet(
                    this, p,
                    new ForwardingPointer(p, new Object[nextSize])))
                resizing = true;
        }
    }

    private Object[]
    helpTransfer(ForwardingPointer a, int targetIndex) {
        ContainerBridge p = tryTransfer(a);

        Object[] array = p instanceof ForwardingPointer f
                ? f.nextCells : p.array;
        Objects.checkIndex(targetIndex, array.length);

        return array;
    }
    private ContainerBridge tryTransfer(ForwardingPointer a) {
        outer : for (int i, last;;) {
            int b = a.bound;
            if ((i = a.getStrideIndex()) < b) {
                if (!a.weakCasStride(i, last = i + a.stride))
                    continue;
            } else {
                i = 0; last = b;
            }
            for (boolean advance = false;;) {
                int committed = 0, fence = Math.min(b, last);
                for (; i < fence; i++) {
                    int p = a.getPendingCount();
                    if (p >= b)
                        return a;
                    else if (p + committed >= b)
                        break;
                    // transferSlot
                    for (Object[] sh = a.array;;) {
                        Object o;
                        if ((o = arrayAt(sh, i)) == a)
                            break;
                        else if (o == null) {
                            if ((o = caeAt(sh, i, null, a)) == null) {
                                committed++; break;
                            }
                            else if (o == a)
                                break;
                        }
                        else if (o instanceof ForwardingPointer f) {
                            sh = f.nextCells;
                            ContainerBridge n = bridge;
                            if (n != a) {
                                if (n instanceof ForwardingPointer r) {
                                    // trying to switch to a newer array
                                    a = r; continue outer;
                                } else
                                    return n; // our mission is over
                            }
                        } else if (o instanceof Cell<?>) {
                            Object v;
                            if ((v = caeAt(a.nextCells, i, null, o)) == null) {
                                v = caeAt(sh, i, o, a);
                                if (v == o) {
                                    committed++; break;
                                }
                                else if (v == a)
                                    break;
                                else
                                    a.nextCells[i] = null;
                            } else if (v == a)
                                break;
                        }
                    }
                }
                if (advance) {
                    a.setPendingCount(b);
                    return a;
                }
                else if (committed > 0 &&
                        a.addAndGetPendingCount(committed) >= b)
                    return a;
                else if (fence < b)
                    continue outer;
                else {
                    // recheck before commit and help
                    advance = true; i = 0;
                }
            }
        }
    }

    static final class ForwardingPointer extends ContainerBridge {
        final int bound; // last index of elements from old to new
        final int stride; // the size of the transfer chunk can be from 1 to fence
        final Object[] nextCells;
        // @Contended
        int strideIndex; // current transfer chunk
        int pendingCount; // total number of transferred chunks

        ForwardingPointer(ContainerBridge prevBridge, Object[] nextArray) {
            super(prevBridge.array);
            this.nextCells = nextArray;
            // calculate the last index
            int n = Math.min(prevBridge.array.length, nextArray.length);
            this.bound = n;
            // threshold
            this.stride = Math.max(MIN_TRANSFER_STRIDE, (n >>> 3) / NCPU);
        }

        int getPendingCount() {
            return (int) PENDINGCOUNT.getOpaque(this);
        }
        void setPendingCount(int c) {
            PENDINGCOUNT.setOpaque(this, c);
        }

        int addAndGetPendingCount(int v) {
            return (int) PENDINGCOUNT.getAndAddRelease(this, v) + v;
        }
        int getStrideIndex() {
            return (int) STRIDEINDEX.getOpaque(this);
        }
        boolean weakCasStride(int c, int v) {
            return STRIDEINDEX.weakCompareAndSet(this, c, v);
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
        final UnblockingArrayBuffer<E> buffer;
        int cursor = -1;
        E next;

        EntrySetItr(UnblockingArrayBuffer<E> buffer) {
            this.buffer = buffer;
        }
        @Override
        @SuppressWarnings("unchecked")
        public boolean hasNext() {
            Object[] arr = buffer.bridge.array;
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
                    next = (E) f.value;
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
            buffer.remove(c);
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
        this.bridge = new ContainerBridge(list.toArray());
    }
    /*
     * Atomic access methods are used for array elements as well
     * as elements of in-progress next array while resizing
     */
    static Object arrayAt(Object[] arr, int i) {
        return AA.getAcquire(arr, i);
    }
    static void setAt(Object[] arr, int i, Object v) {
        AA.setRelease(arr, i, v);
    }
    static boolean weakCasAt(Object[] arr, int i, Object cmp, Object val) {
        return AA.weakCompareAndSet(arr, i, cmp, val);
    }
    static Object caeAt(Object[] arr, int i, Object cmp, Object val) {
        return AA.compareAndExchange(arr, i, cmp, val);
    }

    static class ContainerBridge {

        final Object[] array;

        ContainerBridge(Object[] array) {
            this.array = array;
        }
    }


    static final class Cell<E>{
        volatile E value;
        Cell(E val) { this.value = val; }

        boolean cas(E cmp, E val) {
            return VAL.compareAndSet(this, cmp, val);
        }

        boolean isDead() {
            return value == null;
        }

        @Override
        public String toString() {
            return Objects.toString(value);
        }

        // VarHandle mechanics
        private static final VarHandle VAL;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VAL = l.findVarHandle(Cell.class, "value", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle BRIDGE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BRIDGE = l.findVarHandle(UnblockingArrayBuffer.class,
                    "bridge", ContainerBridge.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}