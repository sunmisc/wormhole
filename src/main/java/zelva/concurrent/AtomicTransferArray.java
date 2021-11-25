package zelva.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;

/**
 * Demo Array Atomic Resize
 * Идея заключаетя в атомарной маркировке старого массива
 * заполняя его специальной TransferNode, в которой есть поле-указатель
 * на новый массив - оно мутабельное
 * новый массив атомарно заполняется элементами из старого массива
 * один за другим
 * для удобной модификации используется Node с волатильным значением
 *
 * @author ZelvaLea
 * @param <E>
 */
public class AtomicTransferArray<E> {
    /*
     *  [T] [1] [2] [3] [4] [5] [6] [7] [8] [9]  |  OLD ARRAY
     *   ^   ^   ^   ^   ^   ^   ^   ^   ^   ^
     *   v   v   v   v   v   v   v   v   v   v
     *  [0]>[N]>[N]>[N]>[N]>[N]>[N]>[N]>[N]>[N]  |  NEW ARRAY
     *
     */
    private static final int INITIAL_CAPACITY = 16;
    volatile Node<E>[] array;
    volatile Node<E>[] transfer;

    @SuppressWarnings("unchecked")
    public AtomicTransferArray(int size) {
        this.array = (Node<E>[]) Array.newInstance(Node.class,
                Math.max(1, size));
    }
    public AtomicTransferArray() {
        this(INITIAL_CAPACITY);
    }

    public boolean compareAndSet(int i, E c, E v) {
        for (Node<E>[] arr = array;;) {
            Node<E> f; E e;
            if ((e = (f = arrayAt(arr, i)).element) != c) {
                return false;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                return VAL.compareAndSet(f, e, v);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public E set(int i, E element) {
        Node<E>[] arr = array;
        boolean rem = element == null;
        for (Node<E> f;;) {
            if ((f = arrayAt(arr, i)) == null) {
                if (rem || weakCasArrayAt(arr, i, null,
                        new Node<>(element))) {
                    return null;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                if (rem) {
                    if (weakCasArrayAt(arr, i, f, null)) {
                        return null;
                    }
                } else {
                    E e;
                    if (element != (e = f.element)) {
                        f.element = element;
                    }
                    return e;
                }
            }
        }
    }

    public E get(int i) {
        for (Node<E>[] arr = array;;) {
            Node<E> f;
            if ((f = arrayAt(arr, i)) == null) {
                return null;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                return f.element;
            }
        }
    }

    public void resize(int size) {
        @SuppressWarnings("unchecked")
        Node<E>[] na = (Node<E>[]) Array.newInstance(Node.class, size);
        transfer(array, na);
    }

    private Node<E>[] transfer(Node<E>[] prev, Node<E>[] next) {
        int p = prev.length, n = next.length;
        if (p == n) return prev;
        for (Node<E> f;;) {
            if (transfer == null &&
                    TRF_ARR.weakCompareAndSet(this, null, next)) {
                final TransferNode<E> tfn = new TransferNode<>(next);
                for (int i = 0, s = Math.min(p, n); i < s; ++i) {
                    if ((f = getAndSetAt(prev, i, tfn)) == null) {
                        continue;
                    } else if (f instanceof TransferNode<E> t) {
                        next = t.transfer;
                        break;
                    }
                    replaceNull(next, i, f);
                }
                transfer = null;
                ARR.compareAndSet(this, prev, next);
                return next;
            }
        }
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> arrayAt(Node<E>[] arr, int i) {
        return (Node<E>) AA.getAcquire(arr, i);
    }
    static <E> void replaceNull(Node<E>[] arr, int i, Node<E> v) {
        AA.compareAndSet(arr, i, null, v);
    }
    static <E> boolean weakCasArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.weakCompareAndSet(arr, i, c, v);
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> getAndSetAt(Node<E>[] arr, int i, Node<E> v) {
        return (Node<E>) AA.getAndSet(arr, i, v);
    }

    public int size() {
        return array.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int i = 0;
        for (Node<E>[] arr = array;;) {
            Node<E> f = arrayAt(arr, i);
            if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                sb.append(f);
                if (++i >= arr.length)
                    return sb.append(']').toString();
                sb.append(", ");
            }
        }
    }

    private static class Node<E> {
        volatile E element;

        Node(E element) {
            this.element = element;
        }
        @Override
        public String toString() {
            E e = element;
            return e == null ? "EmptyNode" : e.toString();
        }
    }

    private static class TransferNode<E> extends Node<E> {
        final Node<E>[] transfer;

        TransferNode(Node<E>[] transfer) {
            super(null);
            this.transfer = transfer;
        }
        @Override
        public String toString() {
            return "TransferNode "+ element;
        }
    }
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle ARR, TRF_ARR, VAL;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ARR = l.findVarHandle(AtomicTransferArray.class, "array", Node[].class);
            TRF_ARR = l.findVarHandle(AtomicTransferArray.class, "transfer", Node[].class);
            VAL = l.findVarHandle(Node.class, "element", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
