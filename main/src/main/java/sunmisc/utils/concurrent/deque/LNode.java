package sunmisc.utils.concurrent.deque;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class LNode<E> implements Node<E> {
    private volatile E item;

    volatile LNode<E> prev, next;

    LNode() { }

    LNode(E item) {
        ITEM.set(this, item);
    }

    @Override
    public E item() {
        return item;
    }

    final boolean casNext(LNode<E> expected, LNode<E> newNode) {
        return NEXT.compareAndSet(this, expected, newNode);
    }

    final boolean casPrev(LNode<E> expected, LNode<E> newNode) {
        return PREV.compareAndSet(this, expected, newNode);
    }
    final void setPrevRelaxed(LNode<E> prev) {
        PREV.set(this, prev);
    }

    final void setNextRelaxed(LNode<E> next) {
        NEXT.set(this, next);
    }

    @Override
    public void updateNext() {
        LNode<E> p, n;
        do {
            if ((n = p = next) == null)
                break;
            for (LNode<E> k; n.item == null && (k = n.next) != null; n = k);
        } while (p != n && !casNext(p, n));
    }

    @Override
    public void updatePrev() {
        LNode<E> p, n;
        do {
            if ((n = p = prev) == null)
                break;
            for (LNode<E> k; n.item == null && (k = n.prev) != null; n = k);
        } while (p != n && !casPrev(p, n));
    }

    @Override
    public boolean tryDelete() {
        return ITEM.getAndSetRelease(this, null) != null;
    }

    @Override public LNode<E> next() { return next; }

    @Override public LNode<E> prev() { return prev; }


    // VarHandle mechanics
    private static final VarHandle ITEM;
    private static final VarHandle PREV, NEXT;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PREV = l.findVarHandle(LNode.class, "prev", LNode.class);
            NEXT = l.findVarHandle(LNode.class, "next", LNode.class);
            ITEM = l.findVarHandle(LNode.class, "item", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
