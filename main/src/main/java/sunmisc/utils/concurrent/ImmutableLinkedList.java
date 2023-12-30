package sunmisc.utils.concurrent;

import java.util.StringJoiner;

public class ImmutableLinkedList<T> {
    private final T value;
    private final ImmutableLinkedList<T> prev, next;

    public ImmutableLinkedList(ImmutableLinkedList<T> prev,
                               ImmutableLinkedList<T> next,
                               T value) {
        this.value = value;
        this.prev = prev;
        this.next = next;
    }

    public ImmutableLinkedList<T> addLast(T value) {
        ImmutableLinkedList<T> newHead = new ImmutableLinkedList<>(
                null, this, value);
        return prev != null
                ? newHead.prependToTail(prev)
                : newHead;
    }

    public ImmutableLinkedList<T> addFirst(T value) {
        ImmutableLinkedList<T> newTail = new ImmutableLinkedList<>(
                this, null, value);
        return next != null
                ? newTail.appendToHead(next)
                : newTail;
    }

    public ImmutableLinkedList<T> pollLast() {
        if (prev == null)
            return null;
        return prev.appendToHead(null);
    }

    public ImmutableLinkedList<T> pollFirst() {
        if (next == null)
            return null;
        return next.prependToTail(null);
    }

    private ImmutableLinkedList<T>
    appendToHead(ImmutableLinkedList<T> head) {
        return new ImmutableLinkedList<>(prev, head, value);
    }


    private ImmutableLinkedList<T>
    prependToTail(ImmutableLinkedList<T> tail) {
        return new ImmutableLinkedList<>(tail, next, value);
    }

    public T element() {
        return value;
    }

    @Override
    public String toString() {
        StringJoiner builder = new StringJoiner(
                ", ", "[", "]");
        ImmutableLinkedList<T> current = this;
        while (current != null) {
            builder.add(String.valueOf(current.element()));
            current = current.next;
        }
        return builder.toString();
    }
}
