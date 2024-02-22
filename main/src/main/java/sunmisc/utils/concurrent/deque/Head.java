package sunmisc.utils.concurrent.deque;

import jdk.internal.vm.annotation.Contended;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Contended
final class Head<E> implements Node<E> {
    private volatile LNode<E> next;

    Head(LNode<E> next) {
        this.next = next;
    }

    void linkFirst(E element) {
        final LNode<E> newNode = new LNode<>(element);
        for (;;) {
            LNode<E> h = next(); // <- help and get
            // relaxed
            newNode.setNextRelaxed(h);
            // fence
            if (h.casPrev(null, newNode)) {
                NEXT.compareAndSet(this, h, newNode);
                break;
            }
        }
    }

    @Override
    public LNode<E> next() {
        for (;;) {
            final LNode<E> t = next;
            LNode<E> pred = t.prev;
            if (pred == null)
                return t;
            LNode<E> k = pred, p;
            for (; k.item() == null && (p = k.prev) != null; k = p);

            if (NEXT.compareAndSet(this, t, k))
                return k;
        }
    }
    @Override public void updateNext() { next.updateNext(); }

    @Override public void updatePrev() { }

    @Override public Node<E> prev() { throw new UnsupportedOperationException(); }

    @Override public E item() { return null; }

    @Override public boolean tryDelete() { throw new UnsupportedOperationException(); }


    // VarHandle mechanics
    private static final VarHandle NEXT;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            NEXT = l.findVarHandle(Head.class, "next", LNode.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
