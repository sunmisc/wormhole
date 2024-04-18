package sunmisc.utils.concurrent.lists;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


class SlicedList<E> extends AbstractList<E> implements List<E> {
    private final Lock lock = new ReentrantLock();
    private final List<E> origin;
    private final int offset;

    private volatile int size;

    public SlicedList(List<E> origin, int offset, int count) {
        this.origin = origin;
        this.offset = offset;
        this.size = count - offset;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(E e) {
        lock.lock();
        try {
            final int n = size;
            origin.add(offset + n, e);
            size = n + 1;
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        lock.lock();
        try {
            final int n = size;
            boolean result = origin.addAll(offset + n, c);
            size = n + c.size();
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        lock.lock();
        try {
            final int n = size;
            boolean result = origin.addAll(offset + index, c);
            size = n + c.size();
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E get(int index) {
        return origin.get(index + offset);
    }

    @Override
    public E set(int index, E element) {
        lock.lock();
        try {
            return origin.set(index + offset, element);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void add(int index, E element) {
        lock.lock();
        try {
            origin.add(index + offset, element);
            size++;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E remove(int index) {
        lock.lock();
        try {
            E val = origin.remove(index + offset);
            size--;
            return val;
        } finally {
            lock.unlock();
        }
    }


    @Override
    public ListIterator<E> listIterator() {
        return new LimitedListIterator<>(origin.listIterator(offset));
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new LimitedListIterator<>(
                origin.listIterator(offset + index));
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return new SlicedList<>(this, fromIndex, toIndex);
    }
    private class LimitedListIterator<T> implements ListIterator<T> {
        private final ListIterator<T> iterator;
        private int count;

        LimitedListIterator(ListIterator<T> iterator) {
            this.iterator = iterator;
            this.count = 0;
        }
        @Override
        public void remove() {
            lock.lock();
            try {
                iterator.remove();
                size--;
            } finally {
                lock.unlock();
            }
            count--;
        }
        @Override
        public void add(T t) {
            lock.lock();
            try {
                iterator.add(t);
                size++;
            } finally {
                lock.unlock();
            }
            count++;
        }
        @Override
        public void set(T t) {
            iterator.set(t);
        }
        @Override
        public boolean hasNext() {
            return count < size && iterator.hasNext();
        }

        @Override
        public T next() {
            T val = iterator.next();
            count++;
            return val;
        }

        @Override
        public boolean hasPrevious() {
            return count > 0 && iterator.hasPrevious();
        }

        @Override
        public T previous() {
            T val = iterator.previous();
            count--;
            return val;
        }

        @Override
        public int nextIndex() {
            return iterator.nextIndex();
        }

        @Override
        public int previousIndex() {
            return iterator.previousIndex();
        }

    }
}
