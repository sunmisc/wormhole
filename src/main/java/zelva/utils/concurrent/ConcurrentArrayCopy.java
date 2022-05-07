package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

public class ConcurrentArrayCopy<E> {
    private static final int MIN_CAPACITY = 1; // todo : 0 or 1?
    private static final int INITIAL_CAPACITY = 16;
    private static final Object MOVED = new Object();

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
                        new MutableNode<>(v)));
            } else if ((e = f.getElement()) != c) {
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
                                new MutableNode<>(element))) {
                    return null;
                }
            } else if (f instanceof TransferNode<E> t) {
                arr = helpTransfer(t);
            } else {
                if (needRemove) {
                    synchronized (f) {
                        if (f == arrayAt(arr, i)) {
                            setAt(arr, i, null);
                            return f.getElement();
                        }
                    }
                } else if (f.getElement() != element) {
                    f.setElement(element);
                    return element;
                }
            }
        }
    }

    public void resize(int size) {
        Node<E>[] prepare = prepareArray(size);
        TransferSourceNode<E> src = new TransferSourceNode<>(prepare, array);
        transferLeft(src.leftHelper);
    }
    private Node<E>[] helpTransfer(TransferNode<E> t) {
        Node<E>[] newArr = t.nextArray();
        if (t instanceof LeftTransferNode<E> ltf) {
            TransferSourceNode<E> src = ltf.source;
            if (src.rightHelper == null) {
                transferRight(src.rightHelper = new RightTransferNode<>(src));
            }
        }
        return newArr;
    }

    public E get(int i) {
        for (Node<E>[] arr = array;;) {
            Node<E> f;
            if ((f = arrayAt(arr, i)) == null) {
                return null;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.nextArray();
            } else {
                return f.getElement();
            }
        }
    }
    public void arrayCopy(int src, int desk) {

    }
    volatile int transferSize; // volatile ?

    // ------------- test ------------- //
    public void transferLeft(LeftTransferNode<E> ltf) {
        final TransferSourceNode<E> src = ltf.source;

        Node<E>[] newArr = src.newArr, shared = src.oldArr;
        final int size = newArr.length;

        outer: for (int i = 0,
                    len = ltf.transferBound(shared.length);
                    i < len; ++i) {
            for (Node<E> f; ; ) {
                if (!src.isLive()) {
                    break outer;
                } else if ((f = arrayAt(shared, i)) == null) {
                    if (weakCasArrayAt(shared, i,
                            null, ltf)) {
                        break;
                    }
                } else if (f instanceof TransferNode<E> t) {
                    if (t.source() == src) {
                        if (f instanceof RightTransferNode) { // finished
                            break outer;
                        }
                        break;
                    } else {
                        shared = helpTransfer(t); // todo: cleanup
                        len = t.transferBound(size);
                    }
                } else {
                    synchronized (f) {
                        if (arrayAt(shared, i) != f) {
                            continue;
                        }
                        setAt(newArr, i, f);
                        setAt(shared, i, ltf); // no cas
                        break;
                    }
                }
            }
        }
        src.oldArr = null; // post
        array = newArr;
    }

    Node<E>[] transferRight(RightTransferNode<E> h) {
        TransferSourceNode<E> src = h.source;
        Node<E>[] oldArr = src.oldArr,
                newArr = src.newArr;
        if (src.isLive()) {
            outer:
            for (int i = h.transferBound(oldArr.length) - 1;
                 i >= 0; --i) {
                for (Node<E> f; ; ) {
                    if (!src.isLive()) {
                        break outer;
                    } else if ((f = arrayAt(oldArr, i)) == null) {
                        if (weakCasArrayAt(oldArr, i,
                                null, h)) {
                            break;
                        }
                    } else if (f instanceof TransferNode<E> t) {
                        if (t.source() == src) {
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
            src.oldArr = null; // post
            array = newArr;
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
                        arr = c.source.newArr; // filled
                        break;
                    } else {
                        f = arrayAt(arr, i);
                    }
                } else {
                    if (f != null)
                        action.accept(f.getElement());
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
                arr = t.nextArray();
            } else if (weakCasArrayAt(arr, i, f, null)) {
                ++i;
            }
        }
    }
    abstract static class Node<E> {
        abstract E getElement();

        void setElement(E element) {throw new UnsupportedOperationException();}

        @Override
        public String toString() {
            E e = getElement();
            return e == null ? "null" : e.toString();
        }
    }

    static class MutableNode<E> extends Node<E> {
        volatile E element;
        MutableNode(E element) {
            this.element = element;
        }
        @Override E getElement() {return element;}

        @Override void setElement(E element) {this.element = element;}
    }

    abstract static class TransferNode<E>
            extends Node<E> {

        @Override
        E getElement() {
            return (E) MOVED;
        }
        int transferBound(int size) {
            return Math.min(nextArray().length, size);
        }

        Node<E>[] nextArray() {
            return source().newArr;
        }

        abstract TransferSourceNode<E> source();
    }

    static class TransferSourceNode<E> {
        // state
        final Node<E>[] newArr;
        Node<E>[] oldArr;


        final LeftTransferNode<E> leftHelper;
        RightTransferNode<E> rightHelper;

        TransferSourceNode(Node<E>[] newArr, Node<E>[] oldArr) {
            this.newArr = newArr;
            this.oldArr = oldArr;
            this.leftHelper = new LeftTransferNode<>(this);
        }

        boolean isLive() {
            return oldArr != null;
        }
    }

    // helper
    static class RightTransferNode<E> extends TransferNode<E> {
        final TransferSourceNode<E> source;

        RightTransferNode(TransferSourceNode<E> source) {
            this.source = source;
        }


        @Override
        TransferSourceNode<E> source() {
            return source;
        }
    }
    static class LeftTransferNode<E> extends TransferNode<E> {
        final TransferSourceNode<E> source;

        LeftTransferNode(TransferSourceNode<E> source) {
            this.source = source;
        }
        @Override
        TransferSourceNode<E> source() {
            return source;
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
                    arr = t.source.newArr; // filled
                    break;
                } else if (f instanceof TransferNode<E> t) {
                    f = arrayAt(t.nextArray(), i);
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
            = MethodHandles.arrayElementVarHandle(Node[].class);
    private static final VarHandle VAL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(MutableNode.class, "element", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}