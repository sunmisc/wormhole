package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ConcurrentArrayCopy<E> {
    private static final int MIN_CAPACITY = 0;
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
                        new Node<>(v)));
            } else if ((e = f.element) != c) {
                return false;
            } else if (f instanceof TransferNode<E> t) {
                arr = helpTransfer(t);
            } else {
                if (needRemove) {
                    if (casArrayAt(arr, i, f, null)) {
                        return true;
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
                    Node<E> e = caeArrayAt(arr, i, f, null);
                    return e.element;
                } else {
                    return (E) VAL.getAndSet(f, element);
                }
            }
        }
    }
    public void resize(int length) {
        resize(0, 0, length);
    }

    public void resize(int srcOff, int dstOff, int length) {
        Node<E>[] prepare = prepareArray(length);

        TransferSourceNode<E> src = new TransferSourceNode<>(
                array, srcOff,
                prepare, dstOff);
        src.left.transfer();
        array = prepare;
    }

    private static <E> Node<E>[] helpTransfer(TransferNode<E> t) {
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
                return f.element;
            }
        }
    }

    private static <E> Node<E>[] prepareArray(int size) {
        return new Node[Math.max(MIN_CAPACITY, size)];
    }

    public int size() {
        return array.length;
    }

    public void forEach(Consumer<? super E> action) { // todo: java heap
        Node<E>[] arr = array;
        for (int i = 0; ;) {
            for (Node<E> f = arrayAt(arr, i);;) {
                if (f instanceof RightTransferNode<E> c) {
                    arr = c.source.next; // filled
                    break;
                } else if (f instanceof TransferNode<E> t) { // left
                    f = arrayAt(t.source.next, i);
                } else {
                    if (f != null) {
                        E val = f.element;
                        if (val != null)
                            action.accept(val);
                    }
                    if (++i == arr.length) // left
                        return;
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

    abstract static class TransferNode<E>
            extends Node<E> {

        final TransferSourceNode<E> source;

        TransferNode(TransferSourceNode<E> source) {
            super(null);
            this.source = source;
        }

        int transferBound(int size) {
            return Math.min(source.next.length, size);
        }

        abstract void transfer();

    }

    static class TransferSourceNode<E> {
        final int srcPos, destPos;
        final Node<E>[] next;
        Node<E>[] prev;

        final LeftTransferNode<E> left; // main
        RightTransferNode<E> rightHelper;

        TransferSourceNode(Node<E>[] prev, int srcPos,
                           Node<E>[] next, int destPos) {
            this.prev = prev;
            this.srcPos = srcPos;
            this.next = next;
            this.destPos = destPos;
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
        void transfer() {
            TransferSourceNode<E> src = source;
            Node<E>[] prev = src.prev, next = src.next;
            if (prev != null) {
                outer:
                for (int i = transferBound(prev.length) - 1,
                     srcPos = src.srcPos + i, destPos = src.destPos + i;
                     i >= 0; --i, --srcPos, --destPos) {

                    for (Node<E> f; ; ) {
                        if (!src.isLive()) {
                            return;
                        } else if ((f = arrayAt(prev, srcPos)) == null) {
                            if (weakCasArrayAt(prev, srcPos,
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
                                if (arrayAt(prev, srcPos) != f) {
                                    continue;
                                }
                                setAt(next, destPos, f);
                                setAt(prev, srcPos, this); // no cas
                                break;
                            }
                        }
                    }
                }
                src.postCompleted();
            }
        }
    }
    static class LeftTransferNode<E> extends TransferNode<E> {

        LeftTransferNode(TransferSourceNode<E> source) {
            super(source);
        }
        @Override
        public void transfer() {
            final TransferSourceNode<E> src = source;

            Node<E>[] next = src.next, shared = src.prev;

            if (shared != null) {
                outer:
                for (int i = 0, nz = next.length,
                     len = transferBound(shared.length),
                     srcPos = src.srcPos, destPos = src.destPos;
                     i < len; ++i, ++srcPos, ++destPos) {
                    for (Node<E> f; ; ) {
                        if (!src.isLive()) {
                            return;
                        } else if ((f = arrayAt(shared, srcPos)) == null) {
                            if (weakCasArrayAt(shared, srcPos,
                                    null, this)) {
                                break;
                            }
                        } else if (f instanceof TransferNode<E> t) {
                            TransferSourceNode<E> t_src = t.source;
                            if (t_src == src) {
                                if (f instanceof RightTransferNode) { // finished
                                    break outer;
                                }
                                break;
                            } else {
                                if ((shared = t_src.prev) == null) {
                                    shared = t_src.next;
                                }
                                len = t.transferBound(nz);
                            }
                        } else {
                            synchronized (f) {
                                if (arrayAt(shared, srcPos) != f) {
                                    continue;
                                }
                                setAt(next, destPos, f);
                                setAt(shared, srcPos, this); // no cas
                                break;
                            }
                        }
                    }
                }
                src.postCompleted();
            }
        }
    }


    @Deprecated
    private static class ArrayIterator {}

    @Override
    public String toString() {
        Node<E>[] arr = array;
        if (arr.length == 0)
            return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0;;) {
            for (Node<E> f = arrayAt(arr, i);;) {
                if (f instanceof RightTransferNode<E> t) {
                    arr = t.source.next; // filled
                    break;
                } else if (f instanceof TransferNode<E> t) { // left
                    f = arrayAt(t.source.next, i);
                } else {
                    sb.append(f);
                    if (++i == arr.length) // last
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

    static <E> Node<E> caeArrayAt(Node<E>[] arr, int i, Node<E> c, Node<E> v) {
        return (Node<E>) AA.compareAndExchange(arr, i, c, v);
    }

    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Node[].class);
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