package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

public class ConcurrentArrayCopyStride<E> {
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    static final int MIN_TRANSFER_STRIDE = 16;
    volatile Cells last;

    public ConcurrentArrayCopyStride(int size) {
        this.last = new Cells(new Object[size]);
    }
    public ConcurrentArrayCopyStride(Object[] array) {
        this.last = new Cells(Arrays.copyOf(array, array.length, Object[].class));
    }
    static class Cells { // value type
        final Object[] array;
        final int fence;
        Cells(Object[] array) {
            this(array, array.length);
        }
        Cells(Object[] array, int fence) {
            this.array = array;
            this.fence = fence;
        }
    }

    public E get(int i) {
        for (Object[] arr = last.array;;) {
            Object o = arrayAt(arr, i);
            if (o instanceof ForwardingPointer t) {
                arr = t.nextArr;
            } else {
                return (E) o;
            }
        }
    }

    public E set(int i, E element) {
        Object[] arr = last.array;
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
        Object[] nextArray = new Object[cap];
        ForwardingPointer fwd; Cells p;
        while (!LAST.weakCompareAndSet(this,
                p = last,
                fwd = new ForwardingPointer(p, nextArray))
        );
        LAST.compareAndSet(this, fwd, new Cells(transfer(fwd)));
    }

    private Object[] transfer(ForwardingPointer a) {
        for (int i;;) {
            if ((i = a.transferIndex) >= a.fence) {
                // recheck before commit and help
                a.transferChunk(0, i);
            } else {
                int ls;
                if (!TRANSFERINDEX.weakCompareAndSet(a, i,
                        ls = i + a.stride)) {
                    continue;
                }
                a.transferChunk(i, ls);
                if (last instanceof ForwardingPointer f) {
                    a = f;
                    continue;
                }
            }
            return a.nextArr;
        }
    }
    public int size() {
        return last.array.length;
    }

    static final class ForwardingPointer extends Cells {
        final int stride;
        final Object[] nextArr;
        volatile int transferIndex, sizeCtl;

        ForwardingPointer(Cells prev, Object[] nextArr) {
            super(prev.array, Math.min(prev.fence, nextArr.length));
            this.nextArr = nextArr;
            int n = fence;
            // threshold
            this.stride = Math.max((n >> 2) / NCPU, Math.min(n, MIN_TRANSFER_STRIDE));
        }
        // 0-5 5-10 10-12..15
        void transferChunk(int start, int end) {
            int i = start;
            for (; i < end && i < fence; ++i) {
                Object[] shared = array;
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
        Object[] arr = last.array;
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

    private static final VarHandle TRANSFERINDEX;
    private static final VarHandle SIZECTL;
    private static final VarHandle LAST;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            TRANSFERINDEX = l.findVarHandle(ForwardingPointer.class, "transferIndex", int.class);
            SIZECTL = l.findVarHandle(ForwardingPointer.class, "sizeCtl", int.class);
            LAST = l.findVarHandle(ConcurrentArrayCopyStride.class, "last", Cells.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}