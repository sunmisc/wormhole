package sunmisc.utils.concurrent.memory;

import sunmisc.utils.Cursor;

import java.util.Objects;
import java.util.function.Consumer;

public interface ReadableMemory<E> {

    E fetch(int index) throws IndexOutOfBoundsException;

    int length();

    default Cursor<E> origin() {
        try {
            return this.length() > 0
                    ? new CursorImpl<>(0, this, this.fetch(0))
                    : Cursor.empty();
        } catch (final IndexOutOfBoundsException e) {
            return Cursor.empty();
        }
    }

    default void forEach(final Consumer<? super E> action) {
        Objects.requireNonNull(action);

        for (Cursor<E> cursor = this.origin();
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
                final int nextIndex = this.index + 1;
                return nextIndex < this.memory.length()
                        ? new CursorImpl<>(nextIndex, this.memory, this.memory.fetch(nextIndex))
                        : Cursor.empty();
            } catch (final IndexOutOfBoundsException e) {
                return Cursor.empty();
            }
        }

        @Override
        public E element() {
            return this.element;
        }
    }
}
