package zelva.utils.concurrent;

import zelva.utils.Cells;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * An array that supports full concurrency retrievals
 * and high expected update concurrency.
 * This array is based entirely on the free-lock mechanism.
 *
 * @author ZelvaLea
 * @param <E> The base class of elements held in this array
 */
public class ConcurrentArrayCells<E> implements Cells<E> {
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

    // Dead node for null elements when wrapping
    static final Index<?> DEAD_NIL = new Index<>() {
        @Override public Object getValue() {return null;}
        @Override public Object getAndSet(Object val) {
            throw new UnsupportedOperationException();}
        @Override public Object cae(Object c, Object v) {
            throw new UnsupportedOperationException();}
        @Override public String toString() {return "null";}
    };
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
    volatile Levels levels; // current array claimant

    public ConcurrentArrayCells(int size) {
        this.levels = new QLevels(new Object[size]);
    }
    public ConcurrentArrayCells(E[] array) {
        int n; Object o;
        // parallelize copy using Stream API?
        Object[] nodes = new Cell[n = array.length];
        for (int i = 0; i < n; ++i) {
            if ((o = array[i]) != null)
                nodes[i] = new Cell<>(o);
        }
        this.levels = new QLevels(nodes);
    }

    /**
     * @return the current length of the array
     */
    @Override
    public int length() {
        return levels.array().length;
    }

    /**
     * Returns the current value of the element at index {@code i}
     *
     * @param i the index
     * @return the current value
     */
    @Override
    public E get(int i) {
        Object[] arr = levels.array();
        for (Object o;;) {
            if ((o = arrayAt(arr, i)) == null) {
                return null;
            } else if (o instanceof ForwardingPointer t) {
                arr = t.newCells;
            } else if (o instanceof Index f) {
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
    public E set(int i, Object newValue) {
        Objects.requireNonNull(newValue);
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                if (weakCasArrayAt(arr, i, null,
                        new Cell<>(newValue))) {
                    return null;
                }
            } else if (o == DEAD_NIL) {
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f).newCells;
            } else if (o instanceof Index n) {
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
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null
                    || o == DEAD_NIL) {
                return null;
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f).newCells;
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
    public E cae(int i, E expectedValue, E newValue) {
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                if (expectedValue == null &&
                        (newValue == null || weakCasArrayAt(arr, i,
                                null,
                                new Cell<>(newValue)))) {
                    return null;
                }
            } else if (o == DEAD_NIL) {
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f).newCells;
            } else if (o instanceof Index n) {
                Object p = n.getValue();
                return (E) (p == expectedValue
                        ? n.cae(expectedValue, newValue)
                        : p);
            }
        }
    }

    /**
     * resize array to {@code i} length
     * @param length new array length
     */
    @Override
    public void resize(int length) {
        final Object[] nextArray = new Object[length];
        ForwardingPointer fwd;
        for (Levels p;;) {
            if (((p = levels) instanceof QLevels) &&
                    p.fence() == length) {
                return;
            } else if (LEVELS.weakCompareAndSet(this, p,
                    fwd = new ForwardingPointer(p, nextArray))) {
                break;
            }
        }
        LEVELS.compareAndSet(this,
                fwd = transfer(fwd),
                new QLevels(fwd.newCells));
    }
    private ForwardingPointer transfer(ForwardingPointer a) {
        for (int i, ls; ; ) {
            if ((i = a.strideIndex) >= a.fence) {
                // recheck before commit and help
                a.transferChunk(0, i);
                return a;
            } else if (STRIDEINDEX.weakCompareAndSet(a, i,
                    ls = i + a.stride)) {
                a.transferChunk(i, ls);
            }
            if (levels instanceof ForwardingPointer f) {
                a = f;
            }
        }
    }

    static final class ForwardingPointer implements Levels {
        final int fence; // last index of elements from old to new
        final int stride; // the size of the transfer chunk can be from 1 to fence
        final Object[] oldCells, newCells;
        volatile int strideIndex; // current transfer chunk
        volatile int sizeCtl; // total number of transferred chunks

        ForwardingPointer(Levels prev, Object[] newCells) {
            this.oldCells = prev.array(); this.newCells = newCells;
            // calculate the last index
            int n = Math.min(prev.fence(), newCells.length);
            this.fence = n;
            // threshold
            this.stride = Math.max((n >>> 2) / NCPU, Math.min(n, MIN_TRANSFER_STRIDE));
        }
        @Override public Object[] array() {return oldCells;}
        @Override public int fence() {return fence;}

        void transferChunk(int start, int end) {
            int i = start, bound = fence;
            Object[] prev = oldCells;
            for (; i < end && i < bound; ++i) {
                Object[] shared = prev;
                for (Object o; ; ) {
                    if (sizeCtl >= bound) {
                        return;
                    } else if ((o = arrayAt(shared, i)) == DEAD_NIL
                            || o instanceof DeadIndex) {
                    } else if (o instanceof
                            ForwardingPointer f) {
                        if (f == this)
                            break;
                        shared = f.newCells;
                        // we read a more up-to-date array
                        // since it is already filled in order
                        // to avoid unnecessary cycles
                        if (f.sizeCtl >= f.fence) {
                            prev = shared;
                            bound = Math.min(bound, f.fence);
                        }
                    } else if (trySwapSlot(o, i, shared, newCells)) {
                        break;
                    }
                }
            }
            int c = i - start, sz;
            do {
                if ((sz = sizeCtl) >= bound) {
                    return;
                }
            } while(!SIZECTL.weakCompareAndSet(this, sz, sz + c));
        }
        boolean trySwapSlot(Object o, int i,
                            Object[] oldCells, Object[] newCells) {
            Object c;
            if ((c = caeArrayAt(oldCells, i, o,
                    o instanceof Cell n ? new DeadIndex(n) : DEAD_NIL))
                    == o) {
                setAt(newCells, i, o);
                // store fence
                setAt(oldCells, i, this);
                return true;
            } else return c == this; // finished
        }
    }
    @Override
    public int hashCode() {
        Object[] arr = levels.array();
        int result = 1, n = arr.length;
        for (int i = 0; i < n; i++) {
            for (Object f = arrayAt(arr, i); ; ) {
                if (f instanceof ForwardingPointer t) {
                    f = arrayAt(t.newCells, i);
                } else if (f instanceof Index o) {
                    Object val = o.getValue();
                    result = 31 * result + (val == null ? 0 : val.hashCode());
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        Object[] arr = levels.array();
        if (arr.length == 0)
            return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; ; ) {
            for (Object f = arrayAt(arr, i); ; ) {
                if (f instanceof ForwardingPointer t) {
                    f = arrayAt(t.newCells, i);
                    continue;
                }
                sb.append(f);
                if (++i == arr.length) // last
                    return sb.append(']').toString();
                sb.append(", ");
                break;
            }
        }
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
    interface Levels {
        Object[] array();
        default int fence() {return array().length;}
    }
    interface Index<E> {
        E getValue();

        E getAndSet(E val);

        E cae(E c, E v);
    }

    record DeadIndex<E>(Index<E>main) implements Index<E> {
        @Override public E getValue() {return main.getValue();}
        @Override public E getAndSet(E val) {return main.getAndSet(val);}

        @Override public E cae(E c, E v) {return main.cae(c,v);}

        @Override public String toString() {return Objects.toString(getValue());}
    }
    static final class Cell<E> implements Index<E> {
        volatile E val;
        Cell(E val) {
            this.val = val;
        }
        @Override public E getValue() {return val;}
        @Override public E getAndSet(E val) {return (E) VAL.getAndSet(this, val);}

        @Override public E cae(E c, E v) {return (E) VAL.compareAndExchange(this, c,v);}

        @Override public String toString() {return Objects.toString(val);}

        private static final VarHandle VAL;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VAL = l.findVarHandle(Cell.class, "val", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    record QLevels(Object[] array) implements Levels {} // inline type


    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle LEVELS;
    private static final VarHandle STRIDEINDEX;
    private static final VarHandle SIZECTL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STRIDEINDEX = l.findVarHandle(ForwardingPointer.class, "strideIndex", int.class);
            SIZECTL = l.findVarHandle(ForwardingPointer.class, "sizeCtl", int.class);
            LEVELS = l.findVarHandle(ConcurrentArrayCells.class, "levels", Levels.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}