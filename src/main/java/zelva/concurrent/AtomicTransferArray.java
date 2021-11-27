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

    public E set(int i, E element) {
        final boolean remove = element == null;
        Node<E>[] arr = array;
        for (Node<E> f;;) {
            if ((f = arrayAt(arr, i)) == null) {
                if (remove || weakCasArrayAt(arr, i, null,
                        new Node<>(element))) {
                    return null;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                if (remove) {
                    if (weakCasArrayAt(arr, i, f, null)) {
                        return null;
                    }
                } else {
                    E e = f.element;
                    return element != e ? f.element = element : e;
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
        final TransferNode<E> tfn = new TransferNode<>(next);
        int p = prev.length, n = next.length;
        if (p != n) {
            outer: for (int i = Math.min(p, n) - 1; i >= 0; i--) {
                for (Node<E> f;;) {
                    if ((f = arrayAt(prev, i)) != null) {
                        if (f instanceof TransferNode<E> t) {
                            next = t.transfer;
                            break outer;
                        } else if (weakCasArrayAt(prev, i, f, tfn)) {
                            setArrayAt(next, i, f);
                            // replaceNull(next, i, f);
                            break;
                        }
                    }
                }
            }
            return array = next;
        }
        return prev;
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> arrayAt(Node<E>[] arr, int i) {
        return (Node<E>) AA.getAcquire(arr, i);
    }
    static <E> void setArrayAt(Node<E>[] arr, int i, Node<E> v) {
        AA.setRelease(arr, i, v);
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
    private static final VarHandle VAL;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(Node.class, "element", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}