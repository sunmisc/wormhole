package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

public class ConcurrentArrayCopyStride<E> {
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    static final int MIN_TRANSFER_STRIDE = 16;
    volatile Cells cells;

    public ConcurrentArrayCopyStride(int size) {
        this.cells = new QCells(new Object[size]);
    }
    public ConcurrentArrayCopyStride(Object[] array) {
        this.cells = new QCells(Arrays.copyOf(
                array, array.length, Object[].class)
        );
    }

    @FunctionalInterface
    interface Cells {
        Object[] array();
        default int fence() {return array().length;}
    }

    record QCells(Object[] array) implements Cells {} // value type

    public E get(int i) {
        for (Object[] arr = cells.array();;) {
            Object o = arrayAt(arr, i);
            if (o instanceof ForwardingPointer t) {
                arr = t.nextArr;
            } else {
                return (E) o;
            }
        }
    }

    public E set(int i, E element) {
        Object[] arr = cells.array();
        for (Object o; ; ) {
            if ((o = arrayAt(arr, i))
                    == element) {
                return element;
            } else if (o instanceof ForwardingPointer f) {
                arr = transfer(f); // help
            } else if (casArrayAt(arr, i, o, element)) {
                return (E) o;
            }
        }
    }
    public void resize(int cap) {
        final Object[] nextArray = new Object[cap];
        ForwardingPointer fwd;
        for (Cells p;;) {
            if (((p = cells) instanceof QCells) &&
                    p.fence() == cap) {
                return;
            } else if (CELLS.weakCompareAndSet(this, p,
                    fwd = new ForwardingPointer(p.array(), nextArray))) {
                break;
            }
        }
        CELLS.compareAndSet(this, fwd, new QCells(transfer(fwd)));
    }

    private Object[] transfer(ForwardingPointer a) {
        for (int i;;) {
            if ((i = a.strideIndex) >= a.fence()) {
                // recheck before commit and help
                a.transferChunk(0, i);
            } else {
                int ls;
                if (!STRIDEINDEX.weakCompareAndSet(a, i,
                        ls = i + a.stride)) {
                    continue;
                }
                a.transferChunk(i, ls);
                if (cells instanceof ForwardingPointer f) {
                    a = f;
                    continue;
                }
            }
            return a.nextArr;
        }
    }
    public int size() {
        return cells.array().length;
    }

    static final class ForwardingPointer implements Cells {
        final int fence, stride;
        final Object[] oldArr, nextArr;
        volatile int strideIndex, sizeCtl;

        ForwardingPointer(Object[] oldArr, Object[] nextArr) {
            this.oldArr = oldArr;
            this.nextArr = nextArr;
            int n = Math.min(oldArr.length, nextArr.length);
            this.fence = n;
            // threshold
            this.stride = Math.max((n >> 2) / NCPU, Math.min(n, MIN_TRANSFER_STRIDE));
        }
        @Override public Object[] array() {return oldArr;}
        @Override public int fence() {return fence;}

        void transferChunk(int start, int end) {
            int i = start;
            for (; i < end && i < fence; ++i) {
                Object[] shared = array();
                for (Object o; ; ) {
                    if (sizeCtl >= fence)
                        return;
                    else if ((o = arrayAt(shared, i))
                            instanceof ForwardingPointer f) {
                        if (f == this)
                            break;
                        shared = f.nextArr;
                    } else {
                        if (o != null)
                            setAt(nextArr, i, o);
                        // VarHandle.fullFence();
                        if (casArrayAt(shared, i, o, this)) {
                            break;
                        }
                    }
                }
            }
            int c = i - start, sz;
            do {
                if ((sz = sizeCtl) >= fence) {
                    return;
                }
            } while(!SIZECTL.weakCompareAndSet(this, sz, sz + c));
        }
    }

    @Override
    public String toString() {
        Object[] arr = cells.array();
        if (arr.length == 0)
            return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; ; ) {
            for (Object f = arrayAt(arr, i); ; ) {
                if (f instanceof ForwardingPointer t) {
                    f = arrayAt(t.nextArr, i);
                } else {
                    sb.append(f);
                    if (++i == arr.length) // last
                        return sb.append(']').toString();
                    sb.append(", ");
                    break;
                }
            }
        }
    }

    static Object arrayAt(Object[] arr, int i) {
        return AA.getAcquire(arr, i);
    }
    static void setAt(Object[] arr, int i, Object v) {
        AA.setRelease(arr, i, v);
    }
    static boolean casArrayAt(Object[] arr, int i, Object c, Object v) {
        return AA.weakCompareAndSet(arr, i, c, v);
    }
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle CELLS;
    private static final VarHandle STRIDEINDEX;
    private static final VarHandle SIZECTL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STRIDEINDEX = l.findVarHandle(ForwardingPointer.class, "strideIndex", int.class);
            SIZECTL = l.findVarHandle(ForwardingPointer.class, "sizeCtl", int.class);
            CELLS = l.findVarHandle(ConcurrentArrayCopyStride.class, "cells", Cells.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}