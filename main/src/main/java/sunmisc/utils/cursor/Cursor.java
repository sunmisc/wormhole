package sunmisc.utils.cursor;

import sunmisc.utils.lazy.Lazy;
import sunmisc.utils.lazy.SimpleLazy;

import java.util.*;
import java.util.function.Supplier;

public interface Cursor<E> {


    boolean exists();

    E element();

    Cursor<E> next();


    // bruh
    default void remove() {
        throw new UnsupportedOperationException();
    }

    final class Empty<E> implements Cursor<E> {

        @Override public boolean exists() { return false; }

        @Override public Cursor<E> next() { throw new UnsupportedOperationException(); }

        @Override public E element() { throw new NoSuchElementException(); }
    }

    final class IteratorAsCursor<E> implements Cursor<E> {
        private final Iterator<E> iterator;
        private final Lazy<E> next;
        private final E item;

        public IteratorAsCursor(Iterator<E> iterator) {
            this(iterator, iterator.next());
        }

        private IteratorAsCursor(Iterator<E> iterator, E item) {
            this(iterator, item, iterator::next);
        }
        private IteratorAsCursor(Iterator<E> iterator,
                                E item,
                                Supplier<E> next) {
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
                    ? new IteratorAsCursor<>(iterator, next.get())
                    : new Empty<>();
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
