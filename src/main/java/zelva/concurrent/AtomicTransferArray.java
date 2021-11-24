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
 * Вот такая шиза пришла в голову дауну
 * Реализация пока что не работает и просто демонстрирует идею
 *
 * @author ZelvaLea
 * @param <E>
 */
public class AtomicTransferArray<E> {
    private static final int INITIAL_CAPACITY = 16;
    volatile Node<E>[] array;
    volatile Node<E>[] transfer;

    @SuppressWarnings("unchecked")
    public AtomicTransferArray(int size) {
        this.array = (Node<E>[]) Array.newInstance(Node.class, size);
    }
    public AtomicTransferArray() {
        this(INITIAL_CAPACITY);
    }

    public boolean compareAndSet(int i, E c, E v) {
        for (Node<E>[] arr = array;;) {
            Node<E> f; E e;
            if ((e = (f = arrTab(arr, i)).element) != c) {
                return false;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                return VAL.compareAndSet(f, e, v);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public E set(E element, int i) {
        Node<E>[] arr = array;
        for (Node<E> f;;) {
            if ((f = arrTab(arr, i)) == null) {
                if (casArrAt(arr, i, null, new Node<>(element))) {
                    return null;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                return (E) VAL.getAndSet(f, element);
            }
        }
    }

    public E get(int i) {
        for (Node<E>[] arr = array;;) {
            Node<E> f;
            if ((f = arrTab(arr, i)) == null) {
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
            if (TRF_ARR.weakCompareAndSet(this, null, next)) {
                final TransferNode<E> tfn = new TransferNode<>(next);
                n = Math.min(p, n);
                for (int i = 0; i < n; ++i) {
                    if ((f = getAndSet(prev, i, tfn)) == null) {
                        continue;
                    } else if (f instanceof TransferNode<E> t) {
                        next = t.transfer;
                        break;
                    }
                    casArrAt(next, i, null, f);
                }
                transfer = null;
                ARR.compareAndSet(this, prev, next);
                return next;
            }
        }
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> arrTab(Node<E>[] arr, int i) {
        return (Node<E>) AA.getAcquire(arr, i);
    }
    static <E> boolean casArrAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.compareAndSet(arr, i, c, v);
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> getAndSet(Node<E>[] arr, int i, Node<E> v) {
        return (Node<E>) AA.getAndSet(arr, i, v);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('[');
        int i = 0;
        for (Node<E>[] arr = array;;) {
            Node<E> f = arrTab(arr, i);
            if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                b.append(f);
                if (++i >= arr.length)
                    return b.append(']').toString();
                b.append(", ");
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
            return String.valueOf(element);
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
