package zelva.utils.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntUnaryOperator;

public abstract class ConcurrentArrayMap<E>
        extends AbstractMap<Integer,E>
        implements ConcurrentMap<Integer,E>/*, todo: ListIterator<E> */ {

    transient KeySetView<E> keySetView;

    public abstract void resize(IntUnaryOperator resize);

    @Override
    public Set<Integer> keySet() {
        KeySetView<E> ks;
        if ((ks = keySetView) != null) return ks;
        return keySetView = new KeySetView<>(this);
    }


    @Override
    public E replace(@NotNull Integer key, @NotNull E value) {
        return put(key,value);
    }


    private static class KeySetView<E> extends AbstractSet<Integer> {
        private final ConcurrentArrayMap<E> map;

        public KeySetView(ConcurrentArrayMap<E> map) {
            this.map = map;
        }

        @Override public Iterator<Integer> iterator() { return new IdxIterator<>(map); }

        @Override public int size() { return map.size(); }
        @Override public void clear() { map.clear(); }
        @Override public boolean contains(Object k) { return map.containsKey(k); }
    }
    private static class IdxIterator<E> implements Iterator<Integer> {
        private final ConcurrentArrayMap<E> map;
        private int index;

        public IdxIterator(ConcurrentArrayMap<E> map) {
            this.map = map;
        }
        @Override
        public boolean hasNext() {
            int sz = map.size();
            return index < sz;
        }

        @Override
        public Integer next() {
            int i = index;
            if (i >= map.size())
                throw new NoSuchElementException();
            index = i + 1;
            return i;
        }
    }

    static class IndexEntry<E> implements Map.Entry<Integer,E> {
        private final int index;
        private final E val;


        public IndexEntry(int index, E val) {
            this.index = index;
            this.val = val;
        }
        @Override
        public Integer getKey() {
            return index;
        }

        @Override
        public E getValue() {
            return val;
        }

        @Override
        public E setValue(E value) {
            throw new UnsupportedOperationException("not supported");
        }
    }
}
