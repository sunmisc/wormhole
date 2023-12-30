package sunmisc.utils.cursor;

import java.util.Iterator;

public interface Cursor<E> {

    boolean hasNext();

    Cursor<E> next();

    E element();

    void remove();


    class CursorAsIterator<E> implements Iterator<E> {

        private Cursor<E> cursor;

        public CursorAsIterator(Cursor<E> origin) {
            this.cursor = origin;
        }

        @Override
        public boolean hasNext() {
            return cursor.hasNext();
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
