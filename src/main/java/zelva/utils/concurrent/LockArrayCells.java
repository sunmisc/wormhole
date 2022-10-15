package zelva.utils.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntUnaryOperator;

public class LockArrayCells<E> extends ConcurrentCells<E> {
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
    private E[] array;

    public LockArrayCells(int cap) {
        this.array = (E[]) new Object[cap];
    }

    public LockArrayCells(E[] array) {
        this.array = (E[]) Arrays.copyOf(array, array.length, Object[].class);
    }
    public E set(int i, E s) {
        w.lock();
        try {
            E d = array[i];
            array[i] = s;
            return d;
        } finally {
            w.unlock();
        }
    }


    @Override
    public E remove(int i) {
        return set(i, null);
    }

    @Override
    public E cae(int i, E c, E v) {
        w.lock();
        try {
            E p = array[i];
            if (p != c)
                return p;
            array[i] = v;
            return c;
        } finally {
            w.unlock();
        }
    }
    @Override
    public void resize(IntUnaryOperator operator) {
        w.lock();
        try {
            E[] arr = array;
            array = Arrays.copyOf(
                    arr,
                    operator.applyAsInt(arr.length)
            );
        } finally {
            w.unlock();
        }
    }

    @Override
    public int length() {
        r.lock();
        try {
            return array.length;
        } finally {
            r.unlock();
        }
    }

    @Override
    public E get(int i) {
        r.lock();
        try {
            return array[i];
        } finally {
            r.unlock();
        }
    }
    @Override
    public String toString() {
        r.lock();
        try {
            return Arrays.toString(array);
        } finally {
            r.unlock();
        }
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return new Itr<>(this);
    }

    static final class Itr<E> implements Iterator<E> {
        final LockArrayCells<E> es;
        int cursor = -1;
        E next;

        Itr(LockArrayCells<E> es) {
            this.es = es;
        }
        @Override
        public boolean hasNext() {
            Object[] arr = es.array; int i;
            if ((i = ++cursor) == arr.length) {
                cursor = -1;
                return false;
            }
            es.r.lock();
            try {
                next = (E) arr[i];
                return true;
            } finally {
                es.r.unlock();
            }
        }
        @Override
        public void remove() {
            final int c = cursor;
            if (c < 0)
                throw new IllegalStateException();
            es.remove(c);
            next = null;
        }

        @Override
        public E next() {
            if (cursor >= 0)
                return next;
            throw new NoSuchElementException();
        }
    }
}