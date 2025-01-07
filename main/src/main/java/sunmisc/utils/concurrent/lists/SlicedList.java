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

    public SlicedList(final List<E> origin, final int offset, final int count) {
        this.origin = origin;
        this.offset = offset;
        this.size = count - offset;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean add(final E e) {
        this.lock.lock();
        try {
            final int n = this.size;
            this.origin.add(this.offset + n, e);
            this.size = n + 1;
            return true;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        this.lock.lock();
        try {
            final int n = this.size;
            final boolean result = this.origin.addAll(this.offset + n, c);
            this.size = n + c.size();
            return result;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends E> c) {
        this.lock.lock();
        try {
            final int n = this.size;
            final boolean result = this.origin.addAll(this.offset + index, c);
            this.size = n + c.size();
            return result;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public E get(final int index) {
        return this.origin.get(index + this.offset);
    }

    @Override
    public E set(final int index, final E element) {
        this.lock.lock();
        try {
            return this.origin.set(index + this.offset, element);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void add(final int index, final E element) {
        this.lock.lock();
        try {
            this.origin.add(index + this.offset, element);
            this.size++;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public E remove(final int index) {
        this.lock.lock();
        try {
            final E val = this.origin.remove(index + this.offset);
            this.size--;
            return val;
        } finally {
            this.lock.unlock();
        }
    }


    @Override
    public ListIterator<E> listIterator() {
        return new LimitedListIterator<>(this.origin.listIterator(this.offset));
    }

    @Override
    public ListIterator<E> listIterator(final int index) {
        return new LimitedListIterator<>(
                this.origin.listIterator(this.offset + index));
    }

    @Override
    public List<E> subList(final int fromIndex, final int toIndex) {
        return new SlicedList<>(this, fromIndex, toIndex);
    }
    private class LimitedListIterator<T> implements ListIterator<T> {
        private final ListIterator<T> iterator;
        private int count;

        LimitedListIterator(final ListIterator<T> iterator) {
            this.iterator = iterator;
            this.count = 0;
        }
        @Override
        public void remove() {
            SlicedList.this.lock.lock();
            try {
                this.iterator.remove();
                SlicedList.this.size--;
            } finally {
                SlicedList.this.lock.unlock();
            }
            this.count--;
        }
        @Override
        public void add(final T t) {
            SlicedList.this.lock.lock();
            try {
                this.iterator.add(t);
                SlicedList.this.size++;
            } finally {
                SlicedList.this.lock.unlock();
            }
            this.count++;
        }
        @Override
        public void set(final T t) {
            this.iterator.set(t);
        }
        @Override
        public boolean hasNext() {
            return this.count < SlicedList.this.size && this.iterator.hasNext();
        }

        @Override
        public T next() {
            final T val = this.iterator.next();
            this.count++;
            return val;
        }

        @Override
        public boolean hasPrevious() {
            return this.count > 0 && this.iterator.hasPrevious();
        }

        @Override
        public T previous() {
            final T val = this.iterator.previous();
            this.count--;
            return val;
        }

        @Override
        public int nextIndex() {
            return this.iterator.nextIndex();
        }

        @Override
        public int previousIndex() {
            return this.iterator.previousIndex();
        }

    }
}
