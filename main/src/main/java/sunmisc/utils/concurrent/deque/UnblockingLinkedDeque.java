package sunmisc.utils.concurrent.deque;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

public class UnblockingLinkedDeque<E> {
    private final Head<E> head;
    private final Tail<E> tail;

    // h <-> share <-> t
    public UnblockingLinkedDeque() {
        final LNode<E> share = new LNode<>();
        this.head = new Head<>(share);
        this.tail = new Tail<>(share);
    }

    public void addLast(E element) {
        tail.linkLast(element);
    }
    public void addFirst(E element) {
        head.linkFirst(element);
    }

    public E remove(E o) {
        for (Node<E> h = head.next(); h != null; h = h.next()) {
            E item = h.item();
            if (Objects.equals(o, item) && h.tryDelete()) {
                unlink(h);
                return item;
            }
        }
        return null;
    }
    public E poll() {
        for (Node<E> h = head; h != null; h = h.next()) {
            E item = h.item();
            if (item != null && h.tryDelete()) {
                unlink(h);
                return item;
            }
        }
        return null;
    }

    private void unlink(Node<E> x) {
        x.updateNext();
        x.updatePrev();
        head.updateNext();
        tail.updatePrev();
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        for (Node<E> x = head; x != null; x = x.next()) {
            E item = x.item();
            if (item != null) action.accept(item);
        }
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(
                ", ", "[", "]");
        forEach(x -> joiner.add(Objects.toString(x)));

        return joiner.toString();
    }
}
