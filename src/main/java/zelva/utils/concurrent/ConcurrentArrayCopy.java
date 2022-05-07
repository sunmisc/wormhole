package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

public class ConcurrentArrayCopy<E> {
    private static final int MIN_CAPACITY = 1; // todo : 0 or 1?
    private static final int INITIAL_CAPACITY = 16;

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
    volatile int transferSize; // volatile ?

    // ------------- test ------------- //
    public void resize(int size) {
        if (transferSize == size)
            return;
        transferSize = size;
        // todo : handle the exception
        final Node<E>[] newArr = prepareArray(size);
        Node<E>[] old;
        final LeftTransferNode<E> ltf = new LeftTransferNode<>(
                newArr, old = array);
        outer: for (int i = 0,
                    len = ltf.transferBound(old.length);
                    i < len; ++i) {
            for (Node<E> f; ; ) {
                if (!ltf.isLive()) {
                    break outer;
                } else if ((f = arrayAt(old, i)) == null) {
                    if (weakCasArrayAt(old, i,
                            null, ltf)) {
                        break;
                    }
                } else if (f instanceof TransferNode<E> t) {
                    if (t.equivalent(ltf)) {
                        if (f instanceof RightTransferNode) { // finished
                            break outer;
                        }
                        break;
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
                        setAt(old, i, ltf); // no cas
                        break;
                    }
                }
            }
        }
        ltf.postComplete();
        array = newArr;
    }

    Node<E>[] helpTransfer(TransferNode<E> tfn) {
        Node<E>[] newArr = tfn.newArr;
        if (tfn instanceof LeftTransferNode<E> source) {
            // safe racing race will not break anything for us,
            // because the field inside the object is declared as the final
            if (source.helper == null) {
                RightTransferNode<E> h =
                        source.helper = new RightTransferNode<>(source);
                Node<E>[] oldArr = h.getOldArray();
                if (h.isLive()) {
                    outer:
                    for (int i = h.transferBound(oldArr.length) - 1;
                         i >= 0; --i) {
                        for (Node<E> f; ; ) {
                            if (!tfn.isLive()) {
                                break outer;
                            } else if ((f = arrayAt(oldArr, i)) == null) {
                                if (weakCasArrayAt(oldArr, i,
                                        null, h)) {
                                    break;
                                }
                            } else if (f instanceof TransferNode<E> t) {
                                if (h.equivalent(t)) {
                                    if (f instanceof LeftTransferNode<E>) { // finished
                                        break outer;
                                    }
                                    Thread.yield();
                                    break;
                                }
                            } else {
                                synchronized (f) {
                                    if (arrayAt(oldArr, i) != f) {
                                        continue;
                                    }
                                    setAt(newArr, i, f);
                                    setAt(oldArr, i, h); // no cas
                                    break;
                                }
                            }
                        }
                    }
                    source.postComplete();
                    array = newArr;
                }
            }
        }
        return newArr;
    }

    // ------------- test ------------- //
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
    // helper
    static class RightTransferNode<E> extends TransferNode<E> {
        final TransferNode<E> source;

        RightTransferNode(TransferNode<E> source) {
            super(source.newArr);
            this.source = source;
        }
        @Override Node<E>[] getOldArray() { return source.getOldArray(); }

        @Override
        boolean equivalent(Node<E> tfn) {
            return tfn == this || source.equivalent(tfn);
        }
    }
    static class LeftTransferNode<E> extends TransferNode<E> {
        Node<E>[] oldArr;
        RightTransferNode<E> helper;

        LeftTransferNode(Node<E>[] newArr, Node<E>[] oldArr) {
            super(newArr);
            this.oldArr = oldArr;
        }

        @Override
        Node<E>[] getOldArray() {
            return oldArr;
        }
        private void postComplete() {
            oldArr = null; // help gc and mark
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
        boolean equivalent(Node<E> tfn) { // todo:
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