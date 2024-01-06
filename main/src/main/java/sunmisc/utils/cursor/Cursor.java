package sunmisc.utils.cursor;

import java.util.Iterator;

public interface Cursor<E> {

    boolean exists();

    Cursor<E> next();

    E element();


    class CursorAsIterator<E> implements Iterator<E> {

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
    }
}
