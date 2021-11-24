package zelva.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * Demo Array Atomic Resize
 * Идея заключаетя в атомарной маркеровке старого массива
 * заполняя его специальной TransferNode, в которой есть поле-указатель
 * на новый массив - оно мутабельное
 * новый массив атомарно заполняется элементами из старого массива
 * один за другим
 * для удобной модификации используется Node с волатильным значеним
 *
 * Вот такая шиза пришла в голову дауну
 * Реализация пока что не работает и просто демонстрирует идею
 *
 * @author ZelvaLea
 * @param <E>
 */
public class AtomicTransferArray<E> {
    public volatile Node<E>[] array = new Node[1];
    volatile Node<E>[] transfer;

    public AtomicTransferArray(Node<E>[] array) {
        this.array = array;
    }
    public AtomicTransferArray() {}

    public Node<E>[] set(E element, int i) {
        Node<E>[] arr = array;
        for (Node<E> f;;) {
            if ((f = arrTab(arr, i)) == null) {
                if (casArrAt(arr, i, null, new Node<>(element))) {
                    return arr;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                f.element = element;
                return arr;
            }
        }
    }
    public void transfer(Node<E>[] prev, Node<E>[] next) {
        int n = next.length;
        for (Node<E>[] na;;) {
            if (((na = transfer) == null || na.length != n) &&
                    TRF_ARR.weakCompareAndSet(this, na, next)) {
                final TransferNode<E> tfn = new TransferNode<>(next);
                int m = Math.min(prev.length, n);
                for (int i = 0; i < m; i++) {
                    Node<E> f = getAndSet(prev, i, tfn);
                    if (f == null) {
                        continue;
                    } else if (f instanceof TransferNode<E> t) {
                        next = t.transfer;
                        break;
                    }
                    casArrAt(next, i, null, f);
                }
                transfer = null;
                if (prev != next)
                    ARR.compareAndSet(this, prev, next);
                return;
            }
        }
    }

    static <E> Node<E> arrTab(Node<E>[] arr, int i) {
        return (Node<E>) AA.getVolatile(arr, i); // acquire
    }
    static <E> boolean casArrAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.compareAndSet(arr, i, c, v);
    }
    static <E> Node<E> getAndSet(Node<E>[] arr, int i, Node<E> v) {
        return (Node<E>) AA.getAndSet(arr, i, v);
    }

    @Override
    public String toString() {
        Node<E>[] arr = array;
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ;) {
            Node<E> f = arrTab(arr, i);
            if (f instanceof TransferNode<E> t) {
                arr = t.transfer;
            } else {
                b.append(f);
                if (arr.length - 1 == i++)
                    return b.append(']').toString();
                b.append(", ");
            }
        }
    }

    public static class Node<E> {
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
    private static final VarHandle ARR, TRF_ARR;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ARR = l.findVarHandle(AtomicTransferArray.class, "array", Node[].class);
            TRF_ARR = l.findVarHandle(AtomicTransferArray.class, "transfer", Node[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
