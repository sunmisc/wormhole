package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

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
public class ConcurrentArrayCopyForbidden<E> {

    volatile Object[] array;

    public ConcurrentArrayCopyForbidden(int size) {
        this.array = new Object[size];
    }
    public ConcurrentArrayCopyForbidden(E[] array) {
        this.array = Arrays.copyOf(array, array.length, Object[].class);
    }

    public E get(int i) {
        for (Object[] arr = array;;) {
            Object f = arrayAt(arr, i);
            if (f instanceof TransferNode t) {
                arr = t.root.newArray;
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
                arr = t.root.newArray;
            } else if (weakCasArrayAt(arr, i, f, element)) {
                return (E) f;
            }
        }
    }


    public void resize(int cap) {
        Object[] prepare = new Object[cap], old;
        int len;
        var fwd = new ForwardingNode(old = array,
                prepare,
                len = Math.min(old.length, cap));
        ARR.compareAndSet(this, old, fwd.left.transfer(len));
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

    record TransferNode(ForwardingNode root, boolean route) {

        Object[] transfer(int bound) {
            ForwardingNode a = root;
            final Object[] shared = a.oldArray, next = a.newArray;
            if (route == RIGHT) {
                for (int i = a.fence - 1;
                     i >= bound && tryTransferNode(a, i, shared, next);
                     --i);
            } else {
                for (int i = 0;
                     i < bound && tryTransferNode(a, i, shared, next);
                     ++i);
            }
            a.postCompleted();
            return a.newArray;
        }

        // false if isFilled
        private boolean tryTransferNode(
                ForwardingNode a, int i,
                Object[] shared, Object[] newArray) {
            for (Object o; ; ) {
                if (a.isFilled()) {
                    return false;
                } else if ((o = arrayAt(shared, i))
                        instanceof TransferNode t) {
                    ForwardingNode p = t.root;
                    boolean s = t.route;
                    if (p == a)
                        return route == s;
                    TransferNode h;
                    if (s == LEFT) {
                        if ((h = p.right) == null)
                            p.right = h = new TransferNode(p, RIGHT);
                    } else {
                        h = p.left;
                    }
                    a.newArray = h.transfer(i);
                    return false;
                } else {
                    if (o != null)
                        setAt(newArray, i, o);
                    if (weakCasPlainArrayAt(shared, i, o, this)) {
                        return true;
                    }
                }
            }
        }
        @Override
        public String toString() {
            return route == LEFT ? "left" : "right";
        }
    }
    private static final boolean LEFT = false;
    private static final boolean RIGHT = true;

    private static class ForwardingNode {
        final int fence;
        volatile Object[] oldArray;
        Object[] newArray;
        final TransferNode left = new TransferNode(this, LEFT);
        TransferNode right;

        ForwardingNode(Object[] oldArray, Object[] newArray, int bound) {
            this.oldArray = oldArray;
            this.newArray = newArray;
            this.fence = bound;
        }

        boolean isFilled() {
            return oldArray == null;
        }
        void postCompleted() {
            oldArray = null;
        }
        void heldLock() {
            while (!isFilled());
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
                if (f instanceof TransferNode t) {
                    if (t.route == RIGHT) {
                        arr = t.root.newArray; // filled
                        break;
                    } else {
                        f = arrayAt(t.root.newArray, i);
                    }
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
            ARR = l.findVarHandle(ConcurrentArrayCopyForbidden.class, "array", Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}