package sunmisc.utils.concurrent;

import org.jetbrains.annotations.NotNull;
import sunmisc.utils.concurrent.lock.SpinReadWriteLock;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.IntUnaryOperator;

/**
 *
 * @author Sunmisc Unsafe
 * @param <E> The base class of elements held in this array
 */
public class ConcurrentArrayMap<E> extends ConcurrentIndexMap<E> {
    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private E[] array;
    transient EntrySetView<E> entrySet;


    public ConcurrentArrayMap(int cap) {
        this.array = (E[]) new Object[cap];
    }

    public ConcurrentArrayMap(E[] array) {
        this.array = (E[]) Arrays.copyOf(array, array.length, Object[].class);
    }
    private final ReadWriteLock rwl = new SpinReadWriteLock();


    @Override
    public E put(Integer i, E s) {
        Lock r = rwl.readLock();

        r.lock();
        try {
            return (E) AA.getAndSet(array, i, s);
        } finally {
            r.unlock();
        }
    }
    @Override
    public void resize(IntUnaryOperator operator) {

        Lock w = rwl.writeLock();
        w.lock();
        try {
            E[] prev = array;
            array = Arrays.copyOf(
                    prev,
                    operator.applyAsInt(prev.length));
            VarHandle.releaseFence(); // release array

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
        Lock r = rwl.readLock();
        r.lock();
        try {
            return (E) AA.compareAndExchange(array, i, c, v);
        } finally {
            r.unlock();
        }
    }

    @Override
    public int size() {
        VarHandle.acquireFence();
        return array.length;
    }

    @Override
    public E get(Object i) {
        VarHandle.acquireFence();
        E[] snap = array;

        return (E) AA.getAcquire(snap);
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
        final ConcurrentArrayMap<E> array;
        EntrySetView(ConcurrentArrayMap<E> array) {
            this.array = array;
        }
        @Override
        public Iterator<Entry<Integer, E>> iterator() {
            return new EntrySetItr<>(array);
        }

        @Override public int size() { return array.size(); }
    }
    static final class EntrySetItr<E> implements Iterator<Map.Entry<Integer,E>> {
        final ConcurrentArrayMap<E> es;
        int cursor = -1;
        E next;

        EntrySetItr(ConcurrentArrayMap<E> es) {
            this.es = es;
        }
        @Override
        public boolean hasNext() {
            Object[] arr = es.array;
            int i;
            if ((i = ++cursor) == arr.length) {
                cursor = -1;
                return false;
            }
            next = (E) arr[i];
            return true;
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