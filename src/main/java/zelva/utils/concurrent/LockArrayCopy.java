package zelva.utils.concurrent;

import zelva.utils.Cells;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LockArrayCopy<E> implements Cells<E> {
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
    private E[] array;

    public LockArrayCopy(int cap) {
        this.array = (E[]) new Object[cap];
    }

    public LockArrayCopy(E[] array) {
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
    public boolean cas(int i, E c, E v) {
        w.lock();
        try {
            if (array[i] != c)
                return false;
            array[i] = v;
            return true;
        } finally {
            w.unlock();
        }
    }
    @Override
    public void resize(int size) {
        E[] newArr = (E[]) new Object[size];
        w.lock();
        try {
            System.arraycopy(
                    array, 0,
                    newArr, 0,
                    Math.min(array.length, size)
            );
            array = newArr;
        } finally {
            w.unlock();
        }
    }

    @Override
    public int size() {
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
}
