package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

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
public class ConcurrentArrayCopy<E> {
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
    private static final int INITIAL_CAPACITY = 16; // todo : 0 or 1?

    volatile Node<E>[] array;

    public ConcurrentArrayCopy(int size) {
        this.array = prepareArray(size);
    }
    public ConcurrentArrayCopy() {
        this(INITIAL_CAPACITY);
    }

    public boolean compareAndSet(int i, E c, E v) {
        final boolean needRemove = v == null;
        for (Node<E>[] arr = array;;) {
            Node<E> f; E e;
            if (((f = arrayAt(arr, i))) == null) {
                return c == null || (!needRemove && casArrayAt(
                        arr, i,
                        null,
                        new Node<>(v)));
            } else if ((e = f.element) != c) {
                return false;
            } else if (f instanceof TransferNode<E> t) {
                arr = helpTransfer(t);
            } else {
                if (needRemove) {
                    synchronized (f) {
                        if (f == arrayAt(arr, i)) {
                            setAt(arr, i, null);
                            return true;
                        }
                    }
                } else {
                    return VAL.compareAndSet(f, e, v);
                }
            }
        }
    }

    public E set(int i, E element) {
        Node<E>[] arr = array;
        final boolean needRemove
                = element == null;
        for (Node<E> f;;) {
            if ((f = arrayAt(arr, i)) == null) {
                if (needRemove ||
                        weakCasArrayAt(arr, i, null,
                        new Node<>(element))) {
                    return null;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = helpTransfer(t);
            } else {
                if (needRemove) {
                    synchronized (f) {
                        if (f == arrayAt(arr, i)) {
                            setAt(arr, i, null);
                            return f.element;
                        }
                    }
                } else if (f.element != element) {
                    return f.element = element;
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
    public void arrayCopy(int src, int desk) {
        
    }

    // ------------- test ------------- //
    public void resize(int size) {
        // todo : handle the exception
        final Node<E>[] newArr = prepareArray(size);
        Node<E>[] old;
        final LeftTransferNode<E> ltfn = new LeftTransferNode<>(
                newArr, old = array);
        outer: for (int i = 0,
                    len = ltfn.transferBound(old.length);
                    i < len; ++i) {
            for (Node<E> f; ; ) {
                if (!ltfn.isLive()) {
                    break outer;
                } else if ((f = arrayAt(old, i)) == null) {
                    if (weakCasArrayAt(old, i,
                            null, ltfn)) {
                        continue outer;
                    }
                } else if (f instanceof TransferNode<E> t) {
                    if (t.equivalent(ltfn)) {
                        if (f instanceof RightTransferNode) { // finished
                            break outer;
                        }
                        continue outer;
                    } else {
                        old = helpTransfer(t);
                        len = t.transferBound(size);
                    }
                } else {
                    synchronized (f) {
                        if (arrayAt(old, i) != f) {
                            continue;
                        }
                        setAt(newArr, i, f);
                        setAt(old, i, ltfn); // no cas
                        continue outer;
                    }
                }
            }
        }
        ltfn.postComplete();
        array = newArr;
    }

    Node<E>[] helpTransfer(final TransferNode<E> tfn) {
        RightTransferNode<E> rtf;
        if (tfn instanceof LeftTransferNode<E> l) {
            // safe racing race will not break anything for us,
            // because the field inside the object is declared as the final
            RightTransferNode<E> h = l.help;
            rtf = h == null ? l.help = new RightTransferNode<>(l) : h;
        } else {
            rtf = (RightTransferNode<E>) tfn;
        }
        Node<E>[] oldArr = rtf.getOldArray(),
                newArr = rtf.newArr;
        if (rtf.isLive()) {
            outer: for (int i = rtf.transferBound(oldArr.length)-1;
                        i >= 0; --i) {
                for (Node<E> f; ; ) {
                    if (!rtf.isLive()) {
                        break outer;
                    } else if ((f = arrayAt(oldArr, i)) == null) {
                        if (weakCasArrayAt(oldArr, i,
                                null, rtf)) {
                            continue outer;
                        }
                    } else if (f instanceof TransferNode<E> t) {
                        if (t.equivalent(tfn)) {
                            break outer;
                        }
                        Thread.yield(); // lost race
                        continue outer;
                    } else {
                        synchronized (f) {
                            if (arrayAt(oldArr, i) != f) {
                                continue;
                            }
                            setAt(newArr, i, f);
                            setAt(oldArr, i, rtf); // no cas
                            continue outer;
                        }
                    }
                }
            }
            rtf.postComplete();
            array = newArr;
        }
        return newArr;
    }
    // ------------- test ------------- //
    @SuppressWarnings("unchecked")
    private static <E> Node<E>[] prepareArray(int size) {
        return new Node[Math.max(MIN_CAPACITY, size)];
    }

    public int size() {
        return array.length;
    }
    public void forEach(Consumer<E> action) {
        Node<E>[] arr = array;
        for (int i = 0; i < arr.length; ++i) {
            for (Node<E> f = arrayAt(arr, i);;) {
                if (f instanceof TransferNode<E>) {
                    if (f instanceof RightTransferNode<E> c) {
                        arr = c.newArr; // filled
                        break;
                    } else {
                        f = arrayAt(arr, i);
                    }
                } else {
                    if (f != null)
                        action.accept(f.element);
                    break;
                }
            }
        }
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

    static class Node<E> {
        volatile E element;

        Node(E element) {
            this.element = element;
        }

        @Override
        public String toString() {
            E e = element;
            return e == null ? "null" : e.toString();
        }
    }
    static class RightTransferNode<E> extends TransferNode<E> {
        final LeftTransferNode<E> source;

        RightTransferNode(LeftTransferNode<E> source) {
            super(source.newArr);
            this.source = source;
        }
        @Override Node<E>[] getOldArray() { return source.getOldArray(); }

        private void postComplete() {
            source.postComplete();
        }

        @Override
        boolean equivalent(Node<E> tfn) {
            return tfn == this || source == tfn;
        }
        @Override
        public String toString() {
            return source.toString() + " " + getClass().getSimpleName();
        }
    }
    static class LeftTransferNode<E> extends TransferNode<E> {
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
        private void postComplete() {
            oldArr = null; // help gc
        }

        @Override
        public String toString() {
            return super.toString()  + " " + getClass().getSimpleName();
        }
    }
    abstract static class TransferNode<E> extends Node<E> {
        // state
        final Node<E>[] newArr;

        TransferNode(Node<E>[] newArr) {
            super(null);
            this.newArr = newArr;
        }
        abstract Node<E>[] getOldArray();

        int transferBound(int size) {
            return Math.min(newArr.length, size);
        }
        boolean isLive() {
            return getOldArray() != null;
        }
        boolean equivalent(Node<E> tfn) {
            return this == tfn;
        }
    }
    @Deprecated
    private static class ArrayIterator {}

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        Node<E>[] arr = array;
        for (int i = 0;;) {
            for (Node<E> f = arrayAt(arr, i);;) {
                if (f instanceof RightTransferNode<E> t) {
                    arr = t.newArr; // filled
                    break;
                } else if (f instanceof TransferNode<E> t) {
                    f = arrayAt(t.newArr, i);
                } else {
                    sb.append(f);
                    if (++i == arr.length) // left
                        return sb.append(']').toString();
                    sb.append(", ");
                    break;
                }
            }
        }
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> arrayAt(Node<E>[] arr, int i) {
        return (Node<E>) AA.getVolatile(arr, i);
    }
    static <E> void setAt(Node<E>[] arr, int i, Node<E> v) {
        AA.setVolatile(arr, i, v);
    }
    static <E> boolean weakCasArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.weakCompareAndSet(arr, i, c, v);
    }
    static <E> boolean casArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return AA.compareAndSet(arr, i, c, v);
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