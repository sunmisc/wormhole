package zelva.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentArrayList<E> {
    volatile Node<E>[] table = new Node[16]; // test
    final LongAdder adder = new LongAdder();

    public Object add(E e) {
        int i = getCount();
        Object o = set(e, i);
        addCount(1);
        return o;
    }

    private void addCount(int c) {
        if (c == 0) return;
        adder.add(1L);
    }
    private int getCount() {
        long n = Math.max(adder.sum(), 0L);
        return n >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
    }

    @SuppressWarnings("unchecked")
    static <E> Node<E> tabAt(Node<E>[] tab, int i) {
        return (Node<E>) AA.getAcquire(tab, i);
    }

    static <E> boolean casTabAt(Node<E>[] tab, int i, Node<E> c, Node<E> v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    @SuppressWarnings("unchecked")
    static <E> Node<E> caeTabAt(Node<E>[] tab, int i, Node<E> c, Node<E> v) {
        return (Node<E>) AA.compareAndExchange(tab, i, c, v);
    }

    @SuppressWarnings("unchecked")
    static <E> Node<E> getAndSetTabAt(Node<E>[] tab, int i,  Node<E> v) {
        return (Node<E>) AA.getAndSet(tab, i, v);
    }

    static <E> void setTabAt(Node<E>[] tab, int i, Node<E> v) {
        AA.setRelease(tab, i, v);
    }
    public Object set(E e, int i) {
        Node<E>[] tab;
        for (Node<E> f;;) {
            if ((f = tabAt(tab = table, i)) == null) { // если еще ни разу не увеличивал
                if (casTabAt(tab, i, null, new Node<>(e))) {
                    break;
                } else {
                    continue;
                }
            } else if (f instanceof ConcurrentArrayList.MovedNode<E>) {
                f = tabAt(tab = ((MovedNode<E>) f).next, i);
            }
            if (f == null && (f = caeTabAt(tab, i, null,
                    new Node<>(e))) == null)
                break;
            f.val = e;
            break;
        }
        int len;
        if (i >= (len = tab.length)-1)
            resize(tab, len);
        return tab;
    }

    public Node<E>[] resize(Node<E>[] tab, int len) {
        Node<E>[] next = new Node[len << 1];
        MovedNode<E> movedNode = new MovedNode<>(next);

        for (int i = 0; i < len; ++i) {
            Node<E> f = getAndSetTabAt(tab, i, movedNode);
            next[i] = f;
        }
        return table = next;
    }

    private static class Node<E> {
        volatile E val;

        Node(E val) {
            this.val = val;
        }

        @Override
        public String toString() {
            return val.toString();
        }
    }
    private static class MovedNode<E> extends Node<E> {
        final Node<E>[] next;
        MovedNode(Node<E>[] next) {
            super(null);
            this.next = next;
        }
    }
    public Node<E>[] getTable() {
        return table;
    }
    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
}
