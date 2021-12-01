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
        this.array = new Node[Math.max(1, size)];
    }
    public AtomicTransferArray() {
        this(INITIAL_CAPACITY);
    }

    public boolean compareAndSet(int i, E c, E v) {
        for (Node<E>[] arr = array;;) {
            Node<E> f; E e;
            if (((f = arrayAt(arr, i))) == null) {
                return c == null || casArrayAt(arr, i, null, new Node<>(v));
            } else if ((e = f.element) != c) {
                return false;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                return VAL.compareAndSet(f, e, v);
            }
        }
    }

    public E set(int i, E element) {
        Node<E>[] arr = array;
        boolean remove = element == null;
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
        Node<E>[] na = new Node[Math.max(1, size)];
        transfer(array, na);
    }

    // test
    private void transfer(Node<E>[] prev, Node<E>[] next) {
        final TransferNode<E> tfn = new TransferNode<>(next);
        int i = 0, s = Math.min(prev.length, next.length); // todo: check
        Node<E>[] last = prev;
        for (Node<E> f; i < s;) { // test
            while ((f = arrayAt(last, i)) instanceof TransferNode<E> t) {
                last = t.transfer;
            }
            if (f == null) {
                if (weakCasArrayAt(last, i, null, tfn)) {
                    last = prev;
                    i++;
                }
            } else {
                synchronized (f) {
                    setAt(next, i, f);
                    if (casArrayAt(last, i, f, tfn)) {
                        last = prev;
                        i++;
                    }
                }
            }
        }
        array = next;
    }

    @SuppressWarnings("unchecked")
    static <E> Node<E> arrayAt(Node<E>[] arr, int i) {
        return (Node<E>) AA.getAcquire(arr, i);
    }
    static <E> void setAt(Node<E>[] arr, int i, Node<E> v) {
        AA.setRelease(arr, i, v);
    }
    static <E> boolean weakCasArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.weakCompareAndSet(arr, i, c, v);
    }
    static <E> boolean casArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.compareAndSet(arr, i, c, v);
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> getAndSetAt(Node<E>[] arr, int i, Node<E> v) {
        return (Node<E>) AA.getAndSet(arr, i, v);
    }

    public int size() {
        return array.length;
    }

    // test
    public void clear() {
        Node<E>[] arr = array;
        for (int i = 0; i < arr.length;) {
            Node<E> f = arrayAt(arr, i);
            if (f == null)
                ++i;
            else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
                i = 0; // reset
            } else if (weakCasArrayAt(arr, i, f, null)) {
                ++i;
            }
        }
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
