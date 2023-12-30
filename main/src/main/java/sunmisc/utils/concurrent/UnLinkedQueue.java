package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class UnLinkedQueue<E> {

    private volatile ImmutableLinkedList<E> last;

    @SuppressWarnings("unchecked")
    public void add(E element) {

        for (ImmutableLinkedList<E> list, next;;) {

            if ((list = (ImmutableLinkedList<E>)
                    LAST.getOpaque(this)) == null)
                next = new ImmutableLinkedList<>(null, null, element);
            else
                next = list.addFirst(element);

            if (LAST.weakCompareAndSetRelease(this, list, next))
                return;
        }
    }
    public void poll() {
        for (ImmutableLinkedList<E> list, next;;) {

            if ((list = (ImmutableLinkedList<E>)
                    LAST.getOpaque(this)) == null)
                return;
            else
                next = list.pollFirst();

            if (LAST.weakCompareAndSetRelease(this, list, next))
                return;
        }
    }

    @Override
    public String toString() {
        ImmutableLinkedList<E> list = last;
        return list == null ? "[]" : list.toString();
    }
    private static final VarHandle LAST;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            LAST = l.findVarHandle(UnLinkedQueue.class,
                    "last", ImmutableLinkedList.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
