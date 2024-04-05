package sunmisc.utils.concurrent.memory;

import sunmisc.utils.cursor.Cursor;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public interface ReadableMemory<E> {

    Optional<E> fetch(int index) throws IndexOutOfBoundsException;

    int length();

    default Cursor<E> origin() {
        return fetch(0)
                .map(x -> (Cursor<E>)new CursorImpl<>(0, this, x))
                .orElseGet(Cursor.Empty::new);
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
                return memory.fetch(nextIndex)
                        .map(x -> (Cursor<E>)new CursorImpl<>(nextIndex, memory, x))
                        .orElseGet(Empty::new);
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
