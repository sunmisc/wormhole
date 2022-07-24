package zelva.utils.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockingArrayCells<E> extends ConcurrentCells<E> {
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
    private E[] array;

    public BlockingArrayCells(int cap) {
        this.array = (E[]) new Object[cap];
    }

    public BlockingArrayCells(E[] array) {
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
    public void resize(int size) {
        w.lock();
        try {
            array = Arrays.copyOf(array, size);
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
        throw new UnsupportedOperationException();
    }
}
