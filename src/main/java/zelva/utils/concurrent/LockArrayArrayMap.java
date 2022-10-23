package zelva.utils.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntUnaryOperator;

public class LockArrayArrayMap<E> extends ConcurrentArrayMap<E> {
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
    private E[] array;
    transient EntrySetView<E> entrySet;

    public LockArrayArrayMap(int cap) {
        this.array = (E[]) new Object[cap];
    }

    public LockArrayArrayMap(E[] array) {
        this.array = (E[]) Arrays.copyOf(array, array.length, Object[].class);
    }
    @Override
    public E put(Integer i, E s) {
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
    public E remove(Object i) {
        return put((int) i, null);
    }

    @NotNull
    @Override
    public Set<Map.Entry<Integer,E>> entrySet() {
        EntrySetView<E> es;
        if ((es = entrySet) != null) return es;
        return entrySet = new EntrySetView<>(this);
    }


    private E cae(int i, E c, E v) {
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
    public int size() {
        r.lock();
        try {
            return array.length;
        } finally {
            r.unlock();
        }
    }

    @Override
    public E get(Object i) {
        r.lock();
        try {
            return array[(int) i];
        } finally {
            r.unlock();
        }
    }

    @Override
    public E putIfAbsent(@NotNull Integer key, E value) {
        return cae(key, null, value);
    }

    @Override
    public boolean remove(@NotNull Object key, Object value) {
        return cae((int)key, (E)value, null) == value;
    }

    @Override
    public boolean replace(@NotNull Integer key, @NotNull E oldValue, @NotNull E newValue) {
        return cae(key,oldValue,newValue) == oldValue;
    }


    static final class EntrySetView<E> extends AbstractSet<Entry<Integer,E>> {
        final LockArrayArrayMap<E> array;
        EntrySetView(LockArrayArrayMap<E> array) {
            this.array = array;
        }
        @Override
        public Iterator<Entry<Integer, E>> iterator() {
            return new EntrySetItr<>(array);
        }

        @Override public int size() { return array.size(); }
    }
    static final class EntrySetItr<E> implements Iterator<Map.Entry<Integer,E>> {
        final LockArrayArrayMap<E> es;
        int cursor = -1;
        E next;

        EntrySetItr(LockArrayArrayMap<E> es) {
            this.es = es;
        }
        @Override
        public boolean hasNext() {
            es.r.lock();
            try {
                Object[] arr = es.array; int i;
                if ((i = ++cursor) == arr.length) {
                    cursor = -1;
                    return false;
                }
                next = (E) arr[i];
                return true;
            } finally {
                es.r.unlock();
            }
        }
        @Override
        public Map.Entry<Integer,E> next() {
            int k = cursor;
            if (k >= 0)
                return new IndexEntry<>(k,next);
            throw new NoSuchElementException();
        }
        @Override
        public void remove() {
            final int c = cursor;
            if (c < 0)
                throw new IllegalStateException();
            es.remove(c);
            next = null;
        }

    }
}