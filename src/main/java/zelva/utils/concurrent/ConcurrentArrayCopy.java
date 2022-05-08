package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.function.Consumer;

public class ConcurrentArrayCopy<E> {
    private static final int MIN_CAPACITY = 1;
    private static final Object MOVED = new Object();

    volatile Node<E>[] array;

    public ConcurrentArrayCopy(int size) {
        this.array = prepareArray(size);
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
                } else {
                    if (f.getElement() != element) {
                        f.setElement(element);
                    }
                    return element;
                }
            }
        }
    }
    volatile int transferSize;

    public void resize(int size) {
        if (transferSize != size) {
            transferSize = size;
            Node<E>[] prepare = prepareArray(size);

            TransferSourceNode<E> src = new TransferSourceNode<>(prepare, array);
            src.left.transfer();
            array = prepare;
        }
    }
    private Node<E>[] helpTransfer(TransferNode<E> t) {
        TransferSourceNode<E> src = t.source;
        if (src.rightHelper == null) {
            (src.rightHelper = new RightTransferNode<>(src)).transfer();
        }
        return t.source.next;
    }

    public E get(int i) {
        for (Node<E>[] arr = array;;) {
            Node<E> f;
            if ((f = arrayAt(arr, i)) == null) {
                return null;
            } else if (f instanceof TransferNode<E> t) {
                arr = t.source.next;
            } else {
                return f.getElement();
            }
        }
    }
    public void arrayCopy(int src, int desk) {

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
                        arr = c.source.next; // filled
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
                arr = t.source.next;
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

        final TransferSourceNode<E> source;

        TransferNode(TransferSourceNode<E> source) {
            this.source = source;
        }
        @Override E getElement() {return null;}

        int transferBound(int size) {
            return Math.min(source.next.length, size);
        }

        abstract Node<E>[] transfer();

    }

    static class TransferSourceNode<E> {
        // state
        final Node<E>[] next;
        Node<E>[] prev;

        final LeftTransferNode<E> left; // main
        RightTransferNode<E> rightHelper;

        TransferSourceNode(Node<E>[] next, Node<E>[] prev) {
            this.next = next;
            this.prev = prev;
            this.left = new LeftTransferNode<>(this);
        }

        boolean isLive() {
            return prev != null;
        }
        void postCompleted() {
            prev = null;
        }
    }

    // helper
    static class RightTransferNode<E> extends TransferNode<E> {

        RightTransferNode(TransferSourceNode<E> source) {
            super(source);
        }

        @Override
        Node<E>[] transfer() {
            TransferSourceNode<E> src = source;
            Node<E>[] prev = src.prev, next = src.next;
            if (prev != null) {
                outer:
                for (int i = transferBound(prev.length) - 1;
                     i >= 0; --i) {
                    for (Node<E> f; ; ) {
                        if (!src.isLive()) {
                            return next;
                        } else if ((f = arrayAt(prev, i)) == null) {
                            if (weakCasArrayAt(prev, i,
                                    null, this)) {
                                break;
                            }
                        } else if (f instanceof TransferNode<E> t) {
                            if (t.source == src) {
                                if (f instanceof LeftTransferNode<E>) { // finished
                                    break outer;
                                }
                                Thread.yield();
                                break;
                            }
                        } else {
                            synchronized (f) {
                                if (arrayAt(prev, i) != f) {
                                    continue;
                                }
                                setAt(next, i, f);
                                setAt(prev, i, this); // no cas
                                break;
                            }
                        }
                    }
                }
                src.postCompleted();
            }
            return next;
        }
    }
    static class LeftTransferNode<E> extends TransferNode<E> {

        LeftTransferNode(TransferSourceNode<E> source) {
            super(source);
        }
        @Override
        public Node<E>[] transfer() {
            final TransferSourceNode<E> src = source;

            Node<E>[] next = src.next, shared = src.prev;

            if (shared != null) {
                outer:
                for (int i = 0, nz = next.length,
                     len = transferBound(shared.length);
                     i < len; ++i) {
                    for (Node<E> f; ; ) {
                        if (!src.isLive()) {
                            return next;
                        } else if ((f = arrayAt(shared, i)) == null) {
                            if (weakCasArrayAt(shared, i,
                                    null, this)) {
                                break;
                            }
                        } else if (f instanceof TransferNode<E> t) {
                            if (t.source == src) {
                                if (f instanceof RightTransferNode) { // finished
                                    break outer;
                                }
                                break;
                            } else {
                                if ((shared = t.source.prev) == null) {
                                    shared = t.source.next;
                                }
                                // shared = helpTransfer(t);
                                len = t.transferBound(nz);
                            }
                        } else {
                            synchronized (f) {
                                if (arrayAt(shared, i) != f) {
                                    continue;
                                }
                                setAt(next, i, f);
                                setAt(shared, i, this); // no cas
                                break;
                            }
                        }
                    }
                }
                src.postCompleted();
            }
            return next;
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
                    arr = t.source.next; // filled
                    break;
                } else if (f instanceof TransferNode<E> t) { // left
                    f = arrayAt(t.source.next, i);
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