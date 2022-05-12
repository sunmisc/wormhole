package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;

/*
 * The operation of transferring data from one array to another is thread-safe,
 * for this you need to mark the transferred nodes as TransferNode in the old array,
 * it is important that if the stream encounters a TransferNode in the array,
 * the new array must contain the current value
 * this access guarantees only the current element, not the entire new array,
 * since other elements of the array can be assigned to different TransferNodes

 * helpTransfer helps the first thread to fill the array, i
 * n the current implementation there are two ways to transfer the array -
 * first (from zero index) and from the end

 * the advantage is that if we iterate the array from 0 to the end
 * and encounter a RightTransferNode, we have the guarantee that
 * the following elements after it are completely transferred
 * and vice versa, this allows for faster transfer.
 * flows fill the array as if to meet themselves
 */
public class ConcurrentArrayCopy<E> {

    volatile Object[] array;

    public ConcurrentArrayCopy(int size) {
        this.array = new Object[size];
    }
    public ConcurrentArrayCopy(E[] array) {
        this.array = Arrays.copyOf(array, array.length, Object[].class);
    }

    public E get(int i) {
        for (Object[] arr = array;;) {
            Object f = arrayAt(arr, i);
            if (f instanceof TransferNode t) {
                arr = t.source.next;
            } else {
                return (E) f;
            }
        }
    }

    public E set(int i, E element) {
        Object[] arr = array;
        for (Object f; ; ) {
            if ((f = arrayAt(arr, i))
                    == element) {
                return element;
            } else if (f instanceof TransferNode t) {
                arr = helpTransfer(t);
            } else if (weakCasArrayAt(arr, i, f, element)) {
                return (E) f;
            }
        }
    }

    public E cae(int i, E c, E v) {
        Object[] arr = array;
        for (Object f;;) {
            if ((f = arrayAt(arr, i)) == c) {
                if (weakCasArrayAt(arr, i, c, v))
                    return c;
            } else if (f instanceof TransferNode t) {
                arr = helpTransfer(t);
            } else {
                return (E) f;
            }
        }
    }
    public boolean cas(int i, E c, E v) {
        return cae(i,c,v) == c;
    }


    public void resize(int length) {
        resize(0, 0, length);
    }
    private void resize(int srcOff, int dstOff, int length) {
        Object[] prepare = new Object[length], old;
        var r = new TransferSourceNode(
                old = array, srcOff,
                prepare, dstOff,
                Math.min(old.length-srcOff, length-dstOff)
        );
        ARR.compareAndSet(this, old, r.leftHelper().transfer());
    }

    private static Object[] helpTransfer(TransferNode t) {
        TransferSourceNode src = t.source;
        if (src.right == null) {
            return (src.right = new RightTransferNode(src)).transfer();
        }
        return src.next;
    }

    public int size() {
        return array.length;
    }
    /*
     * safe publication t to TransferNode mutable races in the object
     * itself can be, the main thing is that there is no reordering operation,
     * the operation of setting the object into a new array must be carried
     * out before cas for the old array
     */

    abstract static class TransferNode {

        final TransferSourceNode source;

        TransferNode(TransferSourceNode source) {
            this.source = source;
        }

        Object[] transfer() {
           return help(0);
        }

        abstract Object[] help(int start);
    }

    private static class TransferSourceNode {
        final int srcPos, destPos, length; // todo
        final Object[] next;
        volatile Object[] prev;

        final LeftTransferNode left; // main
        RightTransferNode right;

        TransferSourceNode(Object[] prev, int srcPos,
                           Object[] next, int destPos,
                           int length) {
            this.prev = prev;
            this.srcPos = srcPos;
            this.next = next;
            this.destPos = destPos;
            this.length = length;
            this.left = new LeftTransferNode(this);
        }

        RightTransferNode rightHelper() {
            RightTransferNode r;
            return (r = right) == null
                    ? right = new RightTransferNode(this)
                    : r;
        }
        LeftTransferNode leftHelper() {
            return left;
        }

        boolean isDone() {
            return prev == null;
        }
        void postCompleted() {
            prev = null;
        }
        void heldLock() {
            while (!isDone());
        }
    }

    static final class LeftTransferNode extends TransferNode {
        LeftTransferNode(TransferSourceNode source) {
            super(source);
        }

        @Override
        public Object[] help(int i) {
            TransferSourceNode src = source;
            Object[] shared = src.prev, next = src.next;
            outer:
            for (int len = src.length; i < len; ++i) {
                for (Object f; ; ) {
                    if (src.isDone()) {
                        return next;
                    } else if ((f = arrayAt(shared, i))
                            instanceof TransferNode t) {
                        TransferSourceNode ctn = t.source;

                        if (ctn == src) {
                            if (t instanceof RightTransferNode) {
                                break outer;
                            }
                            Thread.yield();
                            break;
                        } else {
                            next = t instanceof LeftTransferNode
                                    ? ctn.rightHelper().help(i)
                                    : ctn.leftHelper().help(i);
                            break outer;
                        }
                    } else {
                        if (f != null)
                            setAt(next, i, f);
                        if (weakCasPlainArrayAt(shared, i, f, this)) {
                            break;
                        }
                    }
                }
            }
            src.postCompleted();
            return next;
        }
    }

    // helper
    static final class RightTransferNode extends TransferNode {

        RightTransferNode(TransferSourceNode source) {
            super(source);
        }


        @Override
        public Object[] help(int q) {
            TransferSourceNode src = source;
            Object[] shared = src.prev, next = src.next;
            outer: for (int i = src.length-1; i >= q; --i) {
                for (Object f; ; ) {
                    if (src.isDone()) {
                        return next;
                    } else if ((f = arrayAt(shared, i))
                            instanceof TransferNode t) {
                        TransferSourceNode ctn = t.source;

                        if (ctn == src) {
                            if (t instanceof LeftTransferNode) {
                                break outer;
                            }
                            Thread.yield();
                            break;
                        } else {
                            next = t instanceof LeftTransferNode
                                    ? ctn.rightHelper().help(i)
                                    : ctn.leftHelper().help(i);
                            break outer;
                        }
                    } else {
                        if (f != null)
                            setAt(next, i, f);
                        if (weakCasPlainArrayAt(shared, i, f, this)) {
                            break;
                        }
                    }
                }
            }
            src.postCompleted();
            return next;
        }
    }

    @Override
    public String toString() {
        Object[] arr = array;
        if (arr.length == 0)
            return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; ; ) {
            for (Object f = arrayAt(arr, i); ; ) {
                if (f instanceof RightTransferNode t) {
                    arr = t.source.next; // filled
                    break;
                } else if (f instanceof TransferNode t) { // left
                    f = arrayAt(t.source.next, i);
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
    static boolean weakCasArrayAt(Object[] arr, int i, Object c, Object v) {
        return AA.weakCompareAndSet(arr, i, c, v);
    }
    static boolean weakCasPlainArrayAt(Object[] arr, int i, Object c, Object v) {
        return AA.weakCompareAndSetPlain(arr, i, c, v);
    }
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);

    private static final VarHandle ARR;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ARR = l.findVarHandle(ConcurrentArrayCopy.class, "array", Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}