package sunmisc.utils;

import sunmisc.utils.lazy.Lazy;
import sunmisc.utils.lazy.SimpleLazy;

import java.util.Iterator;
import java.util.NoSuchElementException;

public interface Cursor<E> {


    Cursor<?> EMPTY = new Cursor<>() {
        @Override public boolean exists() { return false; }

        @Override public Cursor<Object> next() { throw new UnsupportedOperationException(); }

        @Override public Object element() { throw new NoSuchElementException(); }
    };

    boolean exists();

    E element();

    Cursor<E> next();


    // bruh
    default void remove() {
        throw new UnsupportedOperationException();
    }


    @SuppressWarnings("unchecked")
    static <E> Cursor<E> empty() {
        return (Cursor<E>) EMPTY;
    }

    final class IteratorAsCursor<E> implements Cursor<E> {
        private final Iterator<E> iterator;
        private final Lazy<E,RuntimeException> next;
        private final E item;

        public IteratorAsCursor(Iterator<E> iterator) {
            this(iterator, iterator.next());
        }

        private IteratorAsCursor(Iterator<E> iterator, E item) {
            this(iterator, item, iterator::next);
        }
        private IteratorAsCursor(Iterator<E> iterator,
                                E item,
                                Scalar<E,RuntimeException> next) {
            this.iterator = iterator;
            this.item = item;
            this.next = new SimpleLazy<>(next);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Cursor<E> next() {
            return iterator.hasNext()
                    ? new IteratorAsCursor<>(iterator, next.value())
                    : empty();
        }

        @Override
        public E element() {
            return item;
        }

        @Override
        public void remove() {
            iterator.remove();
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
            final Cursor<E> prev = cursor;
            cursor = cursor.next();
            return prev.element();
        }

        @Override
        public void remove() {
            cursor.remove();
        }
    }
}
