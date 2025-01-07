package sunmisc.utils.concurrent;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntUnaryOperator;

@SuppressWarnings("forRemoval")
@Deprecated
public abstract class ConcurrentIndexMap<E>
        extends AbstractMap<Integer,E>
        implements ConcurrentMap<Integer,E>/*, todo: ListIterator<E> */ {

    private transient KeySetView<E> keySetView;

    public abstract void resize(IntUnaryOperator resize);

    @Override
    public Set<Integer> keySet() {
        final KeySetView<E> ks;
        if ((ks = this.keySetView) != null) {
            return ks;
        }
        return this.keySetView = new KeySetView<>(this);
    }


    @Override
    public E replace(final Integer key, final E value) {
        return put(key,value);
    }


    private static class KeySetView<E> extends AbstractSet<Integer> {
        private final ConcurrentIndexMap<E> map;

        public KeySetView(final ConcurrentIndexMap<E> map) {
            this.map = map;
        }

        @Override public Iterator<Integer> iterator() { return new IdxIterator<>(this.map); }

        @Override public int size() { return this.map.size(); }
        @Override public void clear() {
            this.map.clear(); }
        @Override public boolean contains(final Object k) { return this.map.containsKey(k); }
    }
    private static class IdxIterator<E> implements Iterator<Integer> {
        private final ConcurrentIndexMap<E> map;
        private int index;

        public IdxIterator(final ConcurrentIndexMap<E> map) {
            this.map = map;
        }
        @Override
        public boolean hasNext() {
            final int sz = this.map.size();
            return this.index < sz;
        }

        @Override
        public Integer next() {
            final int i = this.index;
            if (i >= this.map.size()) {
                throw new NoSuchElementException();
            }
            this.index = i + 1;
            return i;
        }
    }

    static class IndexEntry<E> implements Map.Entry<Integer,E> {
        private final int index;
        private final E val;


        public IndexEntry(final int index, final E val) {
            this.index = index;
            this.val = val;
        }
        @Override
        public Integer getKey() {
            return this.index;
        }

        @Override
        public E getValue() {
            return this.val;
        }

        @Override
        public E setValue(final E value) {
            throw new UnsupportedOperationException("not supported");
        }
    }
}
