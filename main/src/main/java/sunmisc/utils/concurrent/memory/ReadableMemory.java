package sunmisc.utils.concurrent.memory;

import sunmisc.utils.Cursor;

import java.util.Objects;
import java.util.function.Consumer;

public interface ReadableMemory<E> {

    E fetch(int index) throws IndexOutOfBoundsException;

    int length();

    default Cursor<E> origin() {
        try {
            return length() > 0
                    ? new CursorImpl<>(0, this, fetch(0))
                    : Cursor.empty();
        } catch (IndexOutOfBoundsException e) {
            return Cursor.empty();
        }
    }

    default void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);

        for (Cursor<E> cursor = origin();
             cursor.exists();
             cursor = cursor.next()) {
            action.accept(cursor.element());
        }
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
            try {
                final int nextIndex = index + 1;
                return nextIndex < memory.length()
                        ? new CursorImpl<>(nextIndex, memory, memory.fetch(nextIndex))
                        : Cursor.empty();
            } catch (IndexOutOfBoundsException e) {
                return Cursor.empty();
            }
        }

        @Override
        public E element() {
            return element;
        }
    }
}
