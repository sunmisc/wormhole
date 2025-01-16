package sunmisc.utils;

import sunmisc.utils.lazy.SimpleLazy;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public interface Cursor<E> {

    Cursor<?> EMPTY = new Cursor<>() {
        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public Cursor<Object> next() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object element() {
            throw new NoSuchElementException();
        }
    };

    boolean exists();

    E element();

    Cursor<E> next();

    // bruh
    default void remove() {
        throw new UnsupportedOperationException();
    }

    default void forEach(final Consumer<E> action) {
        for (Cursor<E> cursor = this; cursor.exists(); cursor = cursor.next()) {
            action.accept(cursor.element());
        }
    }

    @SuppressWarnings("unchecked")
    static <E> Cursor<E> empty() {
        return (Cursor<E>) EMPTY;
    }


    final class IteratorAsCursor<E> implements Cursor<E> {
        private final Iterator<E> iterator;
        private final Scalar<E, RuntimeException> next;
        private final E item;

        public IteratorAsCursor(final Iterator<E> iterator) {
            this(iterator, iterator.next());
        }

        private IteratorAsCursor(final Iterator<E> iterator, final E item) {
            this(iterator, item, new SimpleLazy<>(iterator::next));
        }

        private IteratorAsCursor(final Iterator<E> iterator,
                                 final E item,
                                 final Scalar<E, RuntimeException> next) {
            this.iterator = iterator;
            this.item = item;
            this.next = next;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Cursor<E> next() {
            return this.iterator.hasNext()
                    ? new IteratorAsCursor<>(this.iterator, this.next.value())
                    : empty();
        }

        @Override
        public E element() {
            return this.item;
        }

        @Override
        public void remove() {
            this.iterator.remove();
        }
    }
    final class CursorAsIterator<E> implements Iterator<E> {
        private Cursor<E> cursor;

        public CursorAsIterator(final Cursor<E> origin) {
            this.cursor = origin;
        }

        @Override
        public boolean hasNext() {
            return this.cursor.exists();
        }

        @Override
        public E next() {
            final Cursor<E> prev = this.cursor;
            this.cursor = this.cursor.next();
            return prev.element();
        }

        @Override
        public void remove() {
            this.cursor.remove();
        }
    }
}
