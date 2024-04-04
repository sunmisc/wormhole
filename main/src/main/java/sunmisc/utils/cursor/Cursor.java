package sunmisc.utils.cursor;

import sunmisc.utils.lazy.Lazy;
import sunmisc.utils.lazy.SimpleLazy;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public interface Cursor<E> {


    boolean exists();

    E element();

    Cursor<E> next();

    @SuppressWarnings("forRemoval")
    default void remove() {
        throw new UnsupportedOperationException();
    }

    final class Empty<E> implements Cursor<E> {

        @Override public boolean exists() { return false; }

        @Override public Cursor<E> next() { throw new UnsupportedOperationException(); }

        @Override public E element() { throw new NoSuchElementException(); }
    }

    final class IteratorAsCursor<E> implements Cursor<E> {
        private static final Object EMPTY = new Object();
        private final Iterator<E> iterator;
        private final Lazy<Cursor<E>> next;
        private final E item;

        @SuppressWarnings("unchecked")
        public IteratorAsCursor(Iterator<E> iterator) {
            this(iterator, iterator.hasNext() ? iterator.next() : (E)EMPTY);
        }

        public IteratorAsCursor(Iterator<E> iterator, E item) {
            this(iterator, item, () ->
                    new IteratorAsCursor<>(iterator, iterator.next()));
        }
        public IteratorAsCursor(Iterator<E> iterator,
                                E item,
                                Supplier<Cursor<E>> next) {
            this.iterator = iterator;
            this.item = item;
            this.next = new SimpleLazy<>(next);
        }

        @Override
        public boolean exists() {
            return iterator.hasNext();
        }

        @Override
        public Cursor<E> next() {
            return next.get();
        }

        @Override
        public E element() {
            if (item == EMPTY) throw new NoSuchElementException();
            return item;
        }
    }
    final class CursorAsIterator<E> implements Iterator<E> {

        private Cursor<E> cursor;

        public CursorAsIterator(Cursor<E> origin) {
            this.cursor = origin;
        }

        @Override
        public boolean hasNext() {
            return cursor.exists();
        }

        @Override
        public E next() {
            Cursor<E> prev = cursor;
            cursor = cursor.next();
            return prev.element();
        }

        @Override
        public void remove() {
            cursor.remove();
        }
    }
}
