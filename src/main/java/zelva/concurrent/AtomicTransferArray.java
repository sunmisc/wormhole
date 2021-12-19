package zelva.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Demo Array Atomic Resize
 * Идея заключаетя в атомарной маркировке старого массива
 * заполняя его специальной TransferNode, в которой есть поле-указатель
 * на новый массив - оно мутабельное
 * новый массив атомарно заполняется элементами из старого массива
 * один за другим
 * для удобной модификации используется Node с волатильным значением
 *
 * метод изменения массива подразумевает иерархическое вскарабкивание
 * между массивами в условиях гонки
 * волатильное поле array присваивается, когда перенос завершен
 * там могут лежать актуальные данные, пока не наступает resize
 * он может создать кучу массивов, в которые мы можем успеть что-то записать
 * такие массивы нужно срочно помечать как "переносимые"
 * метод в 1 потоке начинает с нулевого индекса, помощь заполнения
 * в потоке 2 же начинает с конца, это позволяет достичь максимальной
 * производительности, вместо блокировки, мы помогаем выполнить перенос потоку 1
 *
 * ============================================
 * В будущем планируется оптимизировать этот механизм
 * разделить перенос на левую и на правую часть
 * тогда если с правой стороны, я дойду до левой, то есть гарантия,
 * что после этого узла массив заполнен
 * аналогично с другой стороны массива
 * ============================================
 *
 * @since 9
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
     *
     *
     * transfer:
     *
     *   (T)    (T)    (F)
     *      \      \
     *      (T)    (F)
     *      /
     *   (F)
     */
    private static final int MIN_CAPACITY = 1;
    private static final int INITIAL_CAPACITY = 16;

    volatile Node<E>[] array;

    public AtomicTransferArray(int size) {
        this.array = prepareArray(size);
    }
    public AtomicTransferArray() {
        this(INITIAL_CAPACITY);
    }

    public boolean compareAndSet(int i, E c, E v) {
        for (Node<E>[] arr = array;;) {
            Node<E> f; E e;
            if (((f = arrayAt(arr, i))) == null) {
                return c == null ||
                        casArrayAt(arr, i, null, new Node<>(v));
            } else if ((e = f.element) != c) {
                return false;
            } else if (f instanceof TransferNode<E> t) {
                arr = helpTransfer(t);
            } else {
                return VAL.compareAndSet(f, e, v);
            }
        }
    }


    public E set(int i, E element) {
        Node<E>[] arr = array;
        final boolean remove
                = element == null;
        for (Node<E> f;;) {
            if ((f = arrayAt(arr, i)) == null) {
                if (remove || weakCasArrayAt(arr, i, null,
                        new Node<>(element))) {
                    return null;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = helpTransfer(t);
            } else {
                E e = f.element;
                if (remove) {
                    if (weakCasArrayAt(arr, i, f, null)) {
                        return e;
                    }
                } else {
                    return element != e
                            ? f.element = element : e;
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
                arr = t.newArr;
            } else {
                return f.element;
            }
        }
    }
    public void resize(int size) {
        final Node<E>[] newArr = prepareArray(size);
        Node<E>[] oldArr;
        final LeftTransferNode<E> tfn = new LeftTransferNode<>(newArr,
                oldArr = array);
        outer: for (int i = 0,
                    len = tfn.transferBound(oldArr.length);
                    i < len; ++i) {
            if (tfn.handlePossibleFinish())
                return;
            for (Node<E> f; tfn.isLive(); ) {
                if ((f = arrayAt(oldArr, i))
                        instanceof TransferNode<E> t) {
                    if (t.equivalent(tfn)) {
                        if (f instanceof RightTransferNode) { // finished
                            break outer;
                        }
                        Thread.yield(); // lost race
                        continue outer;
                    } else {
                        oldArr = helpTransfer(t);
                        len = t.transferBound(size);
                    }
                } else if (trySwapSlot(i, oldArr, newArr, f, tfn)) {
                    break;
                }
            }
        }
        array = newArr;
        tfn.oldArr = null; // help gc
    }
    Node<E>[] helpTransfer(final TransferNode<E> tfn) {
        RightTransferNode<E> rtfn;
        if (tfn instanceof LeftTransferNode<E> l) {
            // safe racing race will not break anything for us,
            // because the field inside the object is declared as the final
            RightTransferNode<E> h = l.help;
            rtfn = h == null ? l.help = new RightTransferNode<>(l) : h;
        } else {
            rtfn = (RightTransferNode<E>) tfn;
        }
        final Node<E>[] oldArr = rtfn.getOldArray(),
                newArr = rtfn.newArr;
        if (rtfn.isLive(oldArr)) {
            outer: for (int i = rtfn.transferBound(oldArr.length) - 1;
                        i >= 0; --i) {
                if (rtfn.handlePossibleFinish())
                    return newArr;
                for (Node<E> f; rtfn.isLive(); ) {
                    if ((f = arrayAt(oldArr, i)) instanceof TransferNode<E> t) {
                        if (t.equivalent(tfn)) {
                            if (f instanceof LeftTransferNode) { // finished
                                break outer;
                            }
                            // Thread.yield(); // lost race
                            continue outer;
                        } else {
                            helpTransfer(t);
                        }
                    } else if (trySwapSlot(i,
                            oldArr, newArr, f, rtfn)) {
                        continue outer;
                    }
                }
            }
            rtfn.postComplete(oldArr, this);
        }
        return newArr;
    }
    @SuppressWarnings("unchecked")
    private static <E> Node<E>[] prepareArray(int size) {
        return new Node[Math.max(MIN_CAPACITY, size)];
    }
    private static <E> boolean trySwapSlot(int i,
                                           Node<E>[] to, Node<E>[] from,
                                           Node<E> f, Node<E> t) {
        if (f != null) setAt(from, i, f);
        return weakCasArrayAt(to, i, f, t);
    }

    public int size() {
        return array.length;
    }

    // test
    public void clear() {
        Node<E>[] arr = array;
        for (int i = 0; i < arr.length;) {
            Node<E> f = arrayAt(arr, i);
            if (f == null) {
                ++i;
            } else if (f instanceof TransferNode<E> t) {
                arr = helpTransfer(t);
            } else if (weakCasArrayAt(arr, i, f, null)) {
                ++i;
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
    private static class RightTransferNode<E> extends TransferNode<E> {
        final LeftTransferNode<E> source;

        RightTransferNode(LeftTransferNode<E> source) {
            super(source.newArr);
            this.source = source;
        }
        @Override Node<E>[] getOldArray() { return source.getOldArray(); }

        @Override
        void postComplete(Node<E>[] arr, AtomicTransferArray<E> self) {
            source.postComplete(arr, self);
        }

        @Override
        boolean equivalent(Node<E> tfn) {
            return tfn == this || source == tfn;// || newArr.length == tfn.newArr.length;
        }
    }
    private static class LeftTransferNode<E> extends TransferNode<E> {
        volatile Node<E>[] oldArr;
        RightTransferNode<E> help;

        LeftTransferNode(Node<E>[] newArr, Node<E>[] oldArr) {
            super(newArr);
            this.oldArr = oldArr;
        }
        @Override
        Node<E>[] getOldArray() {
            return oldArr;
        }
        @Override
        void postComplete(Node<E>[] arr, AtomicTransferArray<E> self) {
            if (PREV.compareAndSet(this, arr, TRANSFERRED)) {
                self.array = newArr;
                oldArr = null; // help gc
            }
        }
    }
    private abstract static class TransferNode<E> extends Node<E> {
        // state
        static final Node<?>[] TRANSFERRED = new Node[0];
        final Node<E>[] newArr;

        TransferNode(Node<E>[] newArr) {
            super(null);
            this.newArr = newArr;
        }
        abstract Node<E>[] getOldArray();
        abstract void postComplete(Node<E>[] array, AtomicTransferArray<E> source);

        boolean handlePossibleFinish() {
            Node<E>[] o;
            while ((o = getOldArray()) == TRANSFERRED);
            return o == null;
        }
        int transferBound(int size) {
            return Math.min(newArr.length, size);
        }
        boolean isLive(Node<E>[] o) {
            return o != TRANSFERRED && o != null;
        }
        boolean isLive() {
            return isLive(getOldArray());
        }
        boolean equivalent(Node<E> tfn) {
            return this == tfn;// || newArr.length == tfn.newArr.length;
        }
    }
    @Deprecated
    private static class ArrayIterator {}

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int i = 0;
        for (Node<E>[] arr = array;;) {
            Node<E> f = arrayAt(arr, i);
            // todo: optimize
            for (;;) {
                if (f instanceof RightTransferNode<E> t) {
                    arr = t.newArr;
                } else if (f instanceof LeftTransferNode<E> t) {
                    f = arrayAt(t.newArr, i);
                    continue;
                }
                break;
            }
            sb.append(f);
            if (++i >= arr.length)
                return sb.append(']').toString();
            sb.append(", ");
        }
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

    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle VAL, PREV;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(Node.class, "element", Object.class);
            PREV = l.findVarHandle(LeftTransferNode.class, "oldArr", Node[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}