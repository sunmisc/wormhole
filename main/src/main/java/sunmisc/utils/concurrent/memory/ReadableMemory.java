package sunmisc.utils.concurrent.memory;

import sunmisc.utils.cursor.Cursor;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

public interface ReadableMemory<E> {

    E get(int index) throws IndexOutOfBoundsException;

    int length();

    default Cursor<E> origin() {
        return new CursorImpl<>(0, this, get(0));
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
        private static final Object BOUND = new Object();

        @Override
        public boolean exists() {
            return element != BOUND;
        }

        @Override
        public Cursor<E> next() {
            int nextIndex = index + 1;
            try {
                return new CursorImpl<>(nextIndex, memory,
                        memory.get(nextIndex));
            } catch (IndexOutOfBoundsException e) {
                return new CursorImpl<>(nextIndex, memory, (E) BOUND);
            }
        }

        @Override
        public E element() {
            if (exists())
                return element;
            throw new NoSuchElementException();
        }
    }
}
