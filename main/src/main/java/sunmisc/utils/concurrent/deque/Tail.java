package sunmisc.utils.concurrent.deque;

import jdk.internal.vm.annotation.Contended;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Contended
final class Tail<E> implements Node<E> {
    private volatile LNode<E> prev;

    Tail(LNode<E> prev) {
        this.prev = prev;
    }

    void linkLast(E element) {
        final LNode<E> newNode = new LNode<>(element);
        for (;;) {
            LNode<E> t = prev(); // <- help and get
            // relaxed
            newNode.setPrevRelaxed(t);
            // fence
            if (t.casNext(null, newNode)) {
                PREV.compareAndSet(this, t, newNode);
                break;
            }
        }
    }

    @Override
    public LNode<E> prev() {
        for (;;) {
            final LNode<E> t = prev;
            LNode<E> succ = t.next;
            if (succ == null)
                return t;
            LNode<E> k = succ, p;
            for (; k.item() == null && (p = k.next) != null; k = p);
            if (t == k || PREV.compareAndSet(this, t, k))
                return k;
        }
    }
    @Override public void updatePrev() { prev.updatePrev(); }

    @Override public void updateNext() { }

    @Override public Node<E> next() { throw new UnsupportedOperationException(); }

    @Override public E item() { return null; }

    @Override public boolean tryDelete() { throw new UnsupportedOperationException(); }

    // VarHandle mechanics
    private static final VarHandle PREV;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PREV = l.findVarHandle(Tail.class, "prev", LNode.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
