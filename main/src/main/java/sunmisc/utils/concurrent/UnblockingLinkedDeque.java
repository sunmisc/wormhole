package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.function.Consumer;

public class UnblockingLinkedDeque<E> {
    private final Node<E> head, tail;

    public UnblockingLinkedDeque() {
        Node<E> share = new Node<>();
        Node<E> head  = new Node<>();
        Node<E> tail  = new Node<>();

        tail.prev = share;
        head.next = share;

        this.head = head;
        this.tail = tail;
    }

    private Node<E> tail() {
        for (; ; ) {
            final Node<E> t = tail.prev;
            Node<E> succ = t.next;
            if (succ == null)
                return t;
            Node<E> k = tryFindNextActiveNode(succ);

            if (t == k || tail.casPrev(t, k))
                return k;
        }
    }
    private Node<E> head() {
        for (; ; ) {
            final Node<E> t = head.next;

            Node<E> pred = t.prev;
            if (pred == null)
                return t;
            Node<E> k = tryFindPrevActiveNode(pred);

            if (t == k || head.casNext(t, k))
                return k;
        }
    }
    public void add(E element) {
        final Node<E> newNode = new Node<>(element);

        for (; ; ) {
            Node<E> t = tail(); // <- help and get

            // relaxed
            newNode.setPrevRelaxed(t);

            // fence
            if (t.casNext(null, newNode)) {
                tail.casPrev(t, newNode);
                break;
            }
        }
    }

    public E poll() {
        for (Node<E> h = head(); h != null; h = h.next) {

            for (;;) {
                E item = h.item;
                if (item == null) {
                    break;
                } else if (h.casItem(item, null)) {
                    unlink(h);
                    return item;
                }
            }
        }
        return null;
    }

    private void unlink(Node<E> x) {
        Node<E> prev = x.prev, next = x.next;
        if (prev != null) {
            Node<E> activePrev = tryFindPrevActiveNode(prev);
            updateNext(activePrev);
        }
        if (next != null) {
            Node<E> activeNext
                    = tryFindNextActiveNode(next);
            updatePrev(activeNext);
        }
        updateNext(head.next);
        updatePrev(tail.prev);
    }
    private void updatePrev(Node<E> x) {
        Node<E> p, n;
        do {
            if ((p = x.prev) == null)
                break;
            n = tryFindPrevActiveNode(p);
        } while (p != n && !x.casPrev(p, n));
    }

    private void updateNext(Node<E> x) {
        Node<E> p, n;
        do {
            if ((p = x.next) == null)
                break;
            n = tryFindNextActiveNode(p);
        } while (p != n && !x.casNext(p, n));

    }
    Node<E> tryFindNextActiveNode(Node<E> src) {
        Node<E> n = src, p;
        for (; n.isDead() && (p = n.next) != null; n = p);
        return n;
    }
    Node<E> tryFindPrevActiveNode(Node<E> src) {
        Node<E> n = src, p;
        for (; n.isDead() && (p = n.prev) != null; n = p);
        return n;
    }
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        for (Node<E> x = head(); x != null; x = x.next) {
            E item = x.item;
            if (item != null)
                action.accept(item);
        }
    }
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(
                ", ", "[", "]");
        forEach(x -> joiner.add(x.toString()));

        return joiner.toString();
    }
    private static class Node<E> {

        volatile Node<E> prev, next;
        volatile E item;

        Node() { }

        Node(E item) {
            this.item = item;
        }

        boolean isDead() {
            return item == null;
        }

        final boolean casItem(E expected, E newItem) {
            return ITEM.compareAndSet(this, expected, newItem);
        }
        boolean casNext(Node<E> expected, Node<E> newNode) {
            return NEXT.compareAndSet(this, expected, newNode);
        }
        boolean casPrev(Node<E> expected, Node<E> newNode) {
            return PREV.compareAndSet(this, expected, newNode);
        }

        void setPrevRelaxed(Node<E> prev) {
           PREV.set(this, prev);
        }
        void setNextRelaxed(Node<E> next) {
            NEXT.set(this, next);
        }

        // VarHandle mechanics
        private static final VarHandle ITEM;
        private static final VarHandle PREV, NEXT;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                PREV = l.findVarHandle(Node.class, "prev", Node.class);
                NEXT = l.findVarHandle(Node.class, "next", Node.class);
                ITEM = l.findVarHandle(Node.class, "item", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Override
        public String toString() {
            return Objects.toString(item);
        }
    }
}
