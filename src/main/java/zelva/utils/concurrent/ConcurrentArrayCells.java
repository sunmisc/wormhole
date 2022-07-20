package zelva.utils.concurrent;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * An array that supports full concurrency retrievals
 * and high expected update concurrency.
 * This array is based entirely on the free-lock mechanism.
 *
 * @author ZelvaLea
 * @param <E> The base class of elements held in this array
 */
public class ConcurrentArrayCells<E>
        extends ConcurrentCells<E>
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
     * For this we have strideIndex and sizeCtl field strideIndex
     * divides the array into chunks and gives to the streams,
     * and sizeCtl is the total number of chunks that are already filled
     *
     * the memory problem is solved by a partial spin lock,
     * in cases of deleting an element, for this we use a redirect node,
     * which allows us to read/update the value,
     * but does not allow us to remove the node from the array,
     * this is a unique case of deletion and resize
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
    static final int MIN_TRANSFER_STRIDE = 8;

    /* ---------------- Field -------------- */
    transient volatile Shared shared; // current array claimant

    public ConcurrentArrayCells(int size) {
        this.shared = new QShared(new Object[size]);
    }
    public ConcurrentArrayCells(E[] array) {
        int n; Object o;
        // parallelize copy using Stream API?
        Object[] nodes = new Object[n = array.length];
        for (int i = 0; i < n; ++i) {
            if ((o = array[i]) != null)
                nodes[i] = new QCell<>(o);
        }
        this.shared = new QShared(nodes);
    }

    /**
     * @return the current length of the array
     */
    @Override
    public int length() {
        return shared.array().length;
    }

    /**
     * Returns the current value of the element at index {@code i}
     *
     * @param i the index
     * @return the current value
     */
    @Override
    public E get(int i) {
        Object[] arr = shared.array();
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null) {
                return null;
            } else if (o instanceof ForwardingPointer t) {
                arr = t.nextCells;
            } else if (o instanceof Cell f) {
                return (E) f.getValue();
            }
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
    public E set(int i, E newValue) {
        Objects.requireNonNull(newValue);
        Object[] arr = shared.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                if (weakCasArrayAt(arr, i, null,
                        new QCell<>(newValue))) {
                    return null;
                }
            } else if (o instanceof ForwardingPointer f) {
                arr = helpTransfer(f);
            } else if (o instanceof Cell n) {
                return (E) n.getAndSet(newValue);
            }
        }
    }

    /**
     * Remove a cell from the index {@code i}
     *
     * @param i the index
     * @return the previous value
     */
    @Override
    public E remove(int i) {
        Object[] arr = shared.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                return null;
            } else if (o instanceof ForwardingPointer f) {
                arr = helpTransfer(f);
            } else if (o instanceof ForwardingCell) {
                Thread.onSpinWait();
            } else if (o instanceof Cell n &&
                    weakCasArrayAt(arr, i, o, null)) {
                return (E) n.getValue();
            }
        }
    }

    /**
     * Atomically sets the element at index {@code i} to {@code newValue}
     * if the element's current value, referred to as the <em>witness
     * @param i the index
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     */
    @Override
    public E cae(int i, E expectedValue, E newValue) { // todo: check it
        // todo: remove suppots
        Object[] arr = shared.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                if (expectedValue == null &&
                        (newValue == null || weakCasArrayAt(arr, i,
                                null,
                                new QCell<>(newValue)))) {
                    return null;
                }
            } else if (o instanceof ForwardingPointer f) {
                arr = helpTransfer(f);
            } else if (o instanceof Cell n) {
                return (E) n.cae(expectedValue, newValue);
            }
        }
    }

    /**
     * resize array to {@code i} length
     * @param length new array length
     */
    @Override
    public void resize(int length) {
        Object[] nextArray = new Object[length];
        boolean advance = false;
        for (Shared p;;) {
            if (((p = shared) instanceof QShared) &&
                    p.array().length == length) {
                return;
            } else if (advance) {
                // after a successful commit, you can be sure that
                // we will commit either the same array or the next one
                if (p instanceof ForwardingPointer f &&
                        (p = transfer(f)) // recheck
                                instanceof ForwardingPointer rf &&
                        !LEVELS.weakCompareAndSet(
                                this, p,
                                new QShared((rf.nextCells)))
                ) { continue; }
                return;
            } else if ((p instanceof ForwardingPointer f &&
                    f.nextCells.length == length) ||
                    LEVELS.weakCompareAndSet(
                            this, p,
                            new ForwardingPointer(p, nextArray))) {
                advance = true;
            }
        }
    }
    private Object[] helpTransfer(ForwardingPointer a) {
        Shared l = transfer(a);
        return l instanceof ForwardingPointer f
                ? f.nextCells : l.array();
    }
    private Shared transfer(ForwardingPointer a) {
        int i;
        while ((i = a.getAndAddStride(a.stride)) < a.fence) {
            int ls = i + a.stride, s;

            if ((s = a.transferChunk(i, ls)) < 0) {
                return a;
            } else if (ls >= a.fence) {
                break;
            } else if (s > 0 && a.getAndAddCtl(s) >= a.fence) {
                return a;
            }
            // trying to switch to a newer array
            Shared lst = shared;
            if (lst instanceof ForwardingPointer f) {
                a = f;
            } else {
                return lst; // our mission is over
            }
        }
        // recheck before commit and help
        if (a.transferChunk(0, i) > 0) {
            a.sizeCtl = a.fence;
        }
        return a;
    }
    static final class ForwardingPointer implements Shared {
        final int fence; // last index of elements from old to new
        final int stride; // the size of the transfer chunk can be from 1 to fence
        final Object[] prevCells, nextCells; // owning array

        int strideIndex; // current transfer chunk
        int sizeCtl; // total number of transferred chunks

        ForwardingPointer(Shared prev, Object[] nextCells) {
            this.prevCells = prev.array(); this.nextCells = nextCells;
            // calculate the last index
            int n = Math.min(prev.fence(), nextCells.length);
            this.fence = n;
            // threshold
            this.stride = Math.max(MIN_TRANSFER_STRIDE, (n >>> 3) / NCPU);
        }

        @Override public Object[] array() { return prevCells; }

        @Override public int fence() { return fence; }

        int getAndAddCtl(int v) {
            return (int)SIZECTL.getAndAdd(this, v);
        }
        int getAndAddStride(int v) {
            return (int) STRIDEINDEX.getAndAdd(this, v);
        }

        int transferChunk(int s, int end) {
            for (end = Math.min(end, fence); s < end; ++s) {
                for (Object[] sh = prevCells; ; ) {
                    if (sizeCtl >= fence)
                        return -1;
                    Object o;
                    if ((o = arrayAt(sh, s)) == this) {
                        break;
                    } else if (o instanceof ForwardingPointer f) {
                        sh = f.nextCells;
                    } else if (o instanceof ForwardingCell) {
                        // Thread.onSpinWait();
                    } else if (trySwapSlot(o, s, sh)) {
                        break;
                    }
                }
            }
            return end-s;
        }
        boolean trySwapSlot(Object o, int i, Object[] oldCells) {
            Object c;
            if (o instanceof Cell<?> n) {
                // assert nextCells[i] == null;
                if ((c = caeArrayAt(
                        oldCells, i,
                        o, new ForwardingCell<>(n))
                ) == o) {
                    nextCells[i] = o;
                    // store fence
                    setAt(oldCells, i, this);
                    return true;
                }
            } else if ((c = caeArrayAt(
                    oldCells, i,
                    o, this)
            ) == o) {
                return true;
            }
            return c == this;  // finished
        }

        // VarHandle mechanics
        private static final VarHandle STRIDEINDEX;
        private static final VarHandle SIZECTL;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                STRIDEINDEX = l.findVarHandle(ForwardingPointer.class, "strideIndex", int.class);
                SIZECTL = l.findVarHandle(ForwardingPointer.class, "sizeCtl", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
    @NotNull
    @Override
    public Iterator<E> iterator() {
        return new Itr<>(this);
    }
    static final class Itr<E> implements Iterator<E> {
        final ConcurrentArrayCells<E> array;
        int cursor = -1;
        E next;

        Itr(ConcurrentArrayCells<E> array) {
            this.array = array;
        }
        @Override
        public boolean hasNext() {
            Object[] arr = array.shared.array(); int i;
            if ((i = ++cursor) == arr.length) {
                cursor = -1;
                return false;
            }
            for (Object o; ; ) {
                if ((o = arrayAt(arr, i)) == null) {
                    next = null;
                    return true;
                } else if (o instanceof ForwardingPointer t) {
                    arr = t.nextCells;
                } else if (o instanceof Cell<?> f){
                    next = (E) f.getValue();
                    return true;
                }
            }
        }
        @Override
        public void remove() {
            final int c = cursor;
            if (c < 0)
                throw new IllegalStateException();
            array.remove(c);
            next = null;
        }

        @Override
        public E next() {
            if (cursor >= 0)
                return next;
            throw new NoSuchElementException();
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream s)
            throws IOException {
        Object[] arr = shared.array();
        int len = arr.length;
        s.writeInt(len);
        for (int i = 0; i < len; ++i) {
            for (Object o = arrayAt(arr, i);;) {
                if (o == null) {
                    s.writeObject(o);
                    break;
                } else if (o instanceof ForwardingPointer f) {
                    o = arrayAt(f.nextCells,i);
                } else if (o instanceof Cell<?> f) {
                    s.writeObject(f.getValue());
                    break;
                }
            }
        }
    }
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        int c = s.readInt();
        Object[] arr = new Object[c];
        for (int i = 0; i < c; ++i) {
            Object o = s.readObject();
            if (o != null)
                arr[i] = new QCell<>(o);
        }
        this.shared = new QShared(arr);
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
    static boolean weakCasArrayAt(Object[] arr, int i, Object c, Object v) {
        return AA.weakCompareAndSet(arr,i,c,v);
    }
    static Object caeArrayAt(Object[] arr, int i, Object c, Object v) {
        return AA.compareAndExchange(arr,i,c,v);
    }

    @FunctionalInterface
    interface Shared {
        Object[] array();
        default int fence() { return array().length; }
    }
    interface Cell<E> {
        E getValue();

        E getAndSet(E val);

        E cae(E c, E v);
    }

    record ForwardingCell<E>(Cell<E> main) implements Cell<E> {
        @Override public E getValue() {return main.getValue();}
        @Override public E getAndSet(E val) {return main.getAndSet(val);}
        @Override public E cae(E c, E v) {return main.cae(c,v);}
        @Override public String toString() {return Objects.toString(getValue());}
    }
    static final class QCell<E> implements Cell<E> {
        volatile E val;
        QCell(E val) { this.val = val; }
        @Override public E getValue() { return val; }
        @Override public E getAndSet(E val) { return (E) VAL.getAndSet(this, val); }

        @Override public E cae(E c, E v) { return (E) VAL.compareAndExchange(this, c,v); }

        @Override public String toString() { return Objects.toString(val); }

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
            LEVELS = l.findVarHandle(ConcurrentArrayCells.class, "shared", Shared.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}