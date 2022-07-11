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

    // Dead node for null elements when wrapping
    static final Index<Void> FORWARDING_NIL = new Index<>() {
        @Override public Void getValue() {return null;}
        @Override public Void getAndSet(Void val) {
            throw new UnsupportedOperationException();}
        @Override public Void cae(Void c, Void v) {
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
        Object[] arr = new Object[size];
        //Arrays.setAll(arr, k -> new QCell<>(null));
        this.levels = new QLevels(arr);
    }
    public ConcurrentArrayCells(E[] array) {
        int n; Object o;
        // parallelize copy using Stream API?
        Object[] nodes = new Object[n = array.length];
        for (int i = 0; i < n; ++i) {
            if ((o = array[i]) != null)
                nodes[i] = new QCell<>(o);
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
            } else if (o instanceof Index<?> f) {
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
        Object[] arr = levels.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i)) == null) {
                if (weakCasArrayAt(arr, i, null,
                        new QCell<>(newValue))) {
                    return null;
                }
            } else if (o == FORWARDING_NIL) {
                // Thread.onSpinWait();
            } else if (o instanceof ForwardingPointer f) {
                arr = helpTransfer(f);
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
                    || o == FORWARDING_NIL) {
                return null;
            } else if (o instanceof ForwardingPointer f) {
                arr = helpTransfer(f);
            } else if (o instanceof QCell n &&
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
                                new QCell<>(newValue)))) {
                    return null;
                }
            } else if (o == FORWARDING_NIL) {
                if (newValue == null)
                    return null;
                // Thread.onSpinWait();
            } else if (o instanceof ForwardingPointer f) {
                arr = helpTransfer(f);
            } else if (o instanceof Index n) {
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
        for (Levels p;;) {
            if (((p = levels) instanceof QLevels) &&
                    p.array().length == length) {
                return;
            } else if (advance) {
                if (p instanceof ForwardingPointer f &&
                        (p = transfer(f))
                                instanceof ForwardingPointer fp &&
                        !LEVELS.weakCompareAndSet(
                                this, p,
                                new QLevels((fp.newCells)))
                ) { continue; }
                return;
            } else if ((p instanceof ForwardingPointer f &&
                    f.newCells.length == length) ||
                    LEVELS.weakCompareAndSet(
                            this, p,
                            new ForwardingPointer(p, nextArray))) {
                advance = true;
            }
        }
    }
    private Object[] helpTransfer(ForwardingPointer a) {
        Levels l = transfer(a);
        return l instanceof ForwardingPointer f
                ? f.newCells : l.array();
    }

    private Levels transfer(ForwardingPointer a) {
        int i,f;
        for (int ls;;) {
            if ((i = a.strideIndex) >= (f = a.fence)) {
                break;
            } else if (a.weakCasStride(i,
                    ls = i + a.stride) &&
                    a.transferChunk(i, ls)) {
                a.getAndAddCtl(a.stride);
            }
            Levels l = levels;
            if (l instanceof ForwardingPointer fp) {
                a = fp;
            } else {
                return l;
            }
        }
        // recheck before commit and help
        if (a.transferChunk(0, i)) {
            VarHandle.releaseFence(); // emulate volatile stores
            a.sizeCtl = f;
        }
        return a;
    }
    static final class ForwardingPointer implements Levels {
        final int fence; // last index of elements from old to new
        final int stride; // the size of the transfer chunk can be from 1 to fence
        final Object[] oldCells, newCells; // owning array

        int strideIndex; // current transfer chunk
        int sizeCtl; // total number of transferred chunks

        ForwardingPointer(Levels prev, Object[] newCells) {
            this.oldCells = prev.array(); this.newCells = newCells;
            // calculate the last index
            int n = Math.min(prev.fence(), newCells.length);
            this.fence = n;
            // threshold
            this.stride = Math.max(MIN_TRANSFER_STRIDE, (n >>> 3) / NCPU);
        }

        private int getAndAddCtl(int v) {
            return (int)SIZECTL.getAndAdd(this, v);
        }
        private boolean weakCasStride(int c, int v) {
            return STRIDEINDEX.weakCompareAndSet(this, c, v);
        }
        @Override public Object[] array() {return oldCells;}

        @Override public int fence() {return fence;}

        boolean transferChunk(int i, int end) {
            for (Object o; i < end && i < fence; ++i) {
                for (Object[] sh = oldCells; ; ) {
                    VarHandle.acquireFence();
                    if (sizeCtl >= fence) {
                        return false;
                    } else if ((o = arrayAt(sh, i)) == FORWARDING_NIL
                            || o instanceof ForwardingIndex) {
                        Thread.onSpinWait();
                    } else if (o instanceof
                            ForwardingPointer f) {
                        if (f == this)
                            break;
                        sh = f.newCells;
                    } else if (trySwapSlot(o, i, sh, newCells)) {
                        break;
                    }
                }
            }
            return true;
        }
        boolean trySwapSlot(Object o, int i,
                            Object[] oldCells, Object[] newCells) {
            Object c;
            if (o instanceof Index<?> n) {
                if ((c = caeArrayAt(oldCells, i, o, new ForwardingIndex<>(n))) == o) {
                    setAt(newCells, i, o);
                    // store fence
                    setAt(oldCells, i, this);
                    return true;
                }
            } else if ((c = caeArrayAt(oldCells, i, o, this)) == o) {
                return true;
            }
            return c == this;  // finished
        }

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
            Object[] arr = array.levels.array(); int i;
            if ((i = ++cursor) == arr.length) {
                cursor = -1;
                return false;
            }
            for (Object o; ; ) {
                if ((o = arrayAt(arr, i)) == null) {
                    next = null;
                    return true;
                } else if (o instanceof ForwardingPointer t) {
                    arr = t.newCells;
                } else if (o instanceof Index<?> f){
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
        Object[] arr = levels.array();
        int len = arr.length;
        s.writeInt(len);
        for (int i = 0; i < len; ++i) {
            for (Object o = arrayAt(arr, i);;) {
                if (o == null) {
                    s.writeObject(o);
                    break;
                } else if (o instanceof ForwardingPointer f) {
                    o = arrayAt(f.newCells,i);
                } else if (o instanceof Index<?> f) {
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
        this.levels = new QLevels(arr);
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

    record ForwardingIndex<E>(Index<E> main) implements Index<E> {
        @Override public E getValue() {return main.getValue();}
        @Override public E getAndSet(E val) {return main.getAndSet(val);}
        @Override public E cae(E c, E v) {return main.cae(c,v);}
        @Override public String toString() {return Objects.toString(getValue());}
    }
    static final class QCell<E> implements Index<E> {
        volatile E val;
        QCell(E val) {
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
                VAL = l.findVarHandle(QCell.class, "val", Object.class);
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
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            LEVELS = l.findVarHandle(ConcurrentArrayCells.class, "levels", Levels.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}