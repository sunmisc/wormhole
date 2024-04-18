package sunmisc.utils.concurrent.memory;

import sunmisc.utils.Cursor;

import java.util.Objects;
import java.util.function.Consumer;

public interface ReadableMemory<E> {

    E fetch(int index) throws IndexOutOfBoundsException;

    int length();

    default Cursor<E> origin() {
        final E first = fetch(0);
        return first != null
                ? new CursorImpl<>(0, this, first)
                : new Cursor.Empty<>();
    }

    default void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);

        for (Cursor<E> cursor = origin();
             cursor.exists();
             cursor = cursor.next())
            action.accept(cursor.element());
    }

    record CursorImpl<E>(
            int index,
            ReadableMemory<E> memory,
            E element
    ) implements Cursor<E> {

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Cursor<E> next() {
            final int nextIndex = index + 1;
            try {
                final E val = memory.fetch(0);
                return val != null
                        ? new CursorImpl<>(nextIndex, memory, val)
                        : new Cursor.Empty<>();
            } catch (IndexOutOfBoundsException e) {
                return new Empty<>();
            }
        }

        @Override
        public E element() {
            return element;
        }
    }
}
