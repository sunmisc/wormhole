package sunmisc.utils.concurrent.maps;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;

import static java.util.Objects.requireNonNull;

/**
 * A specialized {@link Map} implementation of the map for use with enumeration type keys.
 * <p>All keys in the enumeration map must belong to the same enumeration type,
 * which is explicitly or implicitly specified when creating the map.
 * Enumeration maps are internally represented as arrays.
 * <p>This view is extremely compact and efficient.
 * <p>Enumeration maps are maintained in the natural order of their keys
 * (the order in which enumeration constants are declared).
 * <p>This is reflected in the iterators returned by the collection views
 * ({@code keySet}, {@code entrySet} and {@code values}).
 * <p>The iterators returned by the collection views are poorly consistent:
 * <p>they will never throw a {@link ConcurrentModificationException}
 * and may or may not show the effects of any map changes,
 * which occur during the execution of the iteration.</p>
 * <p>Null keys are not allowed.
 * Zero values are allowed.
 * <p>Attempts to insert a null key will cause a {@link NullPointerException}.
 * <p>However, attempts to check for the presence of a null key or delete it will work properly.
 * <p>This map differs from EnumMap in that it is thread-safe and scales well
 *
 * @author Sunmisc Unsafe
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
@SuppressWarnings("unchecked")
public final class ConcurrentEnumMap<K extends Enum<K>,V>
        implements ConcurrentMap<K,V>, Serializable {
    @Serial
    private static final long serialVersionUID = 9193424923934859345L;
    // An object of the class for the enumeration type of all the keys this map
    private transient Class<? extends K> keyType;
    // element count
    private transient LongAdder counter;
    // All the values comprising K
    private transient K[] keys;
    // Array representation of this map. The ith element is the value to which universe[i]
    private transient V[] table;

    @SuppressWarnings("forRemoval")
    private transient KeySetView<K,V> keySet;
    @SuppressWarnings("forRemoval")
    private transient ValuesView<K,V> values;
    @SuppressWarnings("forRemoval")
    private transient EntrySetView<K,V> entrySet;

    public ConcurrentEnumMap(final Class<? extends K> keyType) {
        this.keyType = keyType;
        this.keys = keyType.getEnumConstants();
        this.table = (V[]) new Object[this.keys.length];
    }
    public ConcurrentEnumMap(final Map<? extends K, ? extends V> m) {
        this.keys = (K[]) m.keySet().toArray(Enum[]::new);
        this.keyType = this.keys[0].getDeclaringClass();
        this.table = (V[]) new Object[this.keyType.getEnumConstants().length];
        this.putAll(m);
    }

    private void addCount(final long c) {
        if (c == 0L) {
            return;
        }
        LongAdder adder = (LongAdder) ADDER.getOpaque(this);
        if (adder == null) {
            final LongAdder newAdder = new LongAdder();
            if ((adder = (LongAdder) ADDER.compareAndExchange(
                    this, null, newAdder)) == null) {
                adder = newAdder;
            }
        }
        adder.add(c);
    }

    @Override
    public int size() {
        final LongAdder adder = (LongAdder) ADDER.getOpaque(this);
        return adder == null ? 0
                // let's handle the overflow
                : Math.clamp(adder.sum(), 0, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        final LongAdder adder = (LongAdder) ADDER.getOpaque(this);
        return adder == null || adder.sum() <= 0L;
    }

    @Override
    public V get(final Object key) {
        return this.checkKey(key)
                ? tabAt(this.table, ((Enum<?>)key).ordinal())
                : null;
    }
    @Override
    public V put(final K key, final V value) {
        requireNonNull(key);
        requireNonNull(value);

        final int i = key.ordinal();
        final V prev = getAndSetAt(this.table, i, value);
        if (prev == null) {
            this.addCount(1L);
        }
        return prev;
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        requireNonNull(m);
        long delta = 0L;
        final V[] tab = this.table;
        for (final Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            final Enum<?> key = e.getKey(); final Object val = e.getValue();
            if (getAndSetAt(tab, key.ordinal(), val) == null) {
                ++delta;
            }
        }
        this.addCount(delta);
    }

    @Override
    public void forEach(final BiConsumer<? super K, ? super V> action) {
        requireNonNull(action);
        final K[] ks = this.keys; final V[] tab = this.table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            final V v = tabAt(tab, i);
            if (v == null) {
                continue;
            }
            action.accept(ks[i], v);
        }
    }

    @Override
    public void clear() {
        long delta = 0L;
        final V[] tab = this.table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            if (tabAt(tab, i) != null &&
                    getAndSetAt(tab, i, null) != null) {
                --delta;
            }
        }
        this.addCount(delta);
    }

    @Override
    public V remove(final Object key) {
        if (this.checkKey(key)) {
            final int i = ((Enum<?>) key).ordinal();
            final V[] tab = this.table; V p = null;
            if (tabAt(tab, i) != null &&
                    (p = getAndSetAt(tab, i, null)) != null) {
                this.addCount(-1L);
            }
            return p;
        }
        return null;
    }

    @Override
    public boolean containsKey(final Object key) {
        return this.get(key) != null;
    }

    @Override
    public boolean containsValue(final Object value) {
        requireNonNull(value);
        final V[] tab = this.table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            if (Objects.equals(tabAt(tab, i), value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        requireNonNull(key);
        requireNonNull(value);
        final int i = key.ordinal();
        final V[] tab = this.table; V p;
        if ((p = tabAt(tab, i)) == null &&
                (p = caeTabAt(tab, i, null, value)) == null) {
            this.addCount(1L);
        }
        return p;
    }

    @Override
    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remapping) {
        requireNonNull(key);
        requireNonNull(remapping);
        final int i = key.ordinal();
        final V[] tab = this.table;
        for (V oldVal;;) {
            final V newVal = remapping.apply(key,
                    oldVal = tabAt(tab, i));
            // strong CAS to minimize function call
            if (casTabAt(tab, i, oldVal, newVal)) {
                this.addCount(oldVal == null ? 1L : newVal == null ? -1L : 0);
                return newVal;
            }
        }
    }
    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> remapping) {
        requireNonNull(key);
        requireNonNull(remapping);
        final int i = key.ordinal();
        final V[] tab = this.table;
        final V oldVal = tabAt(tab, i), newVal;
        if (oldVal != null ||
                (newVal = remapping.apply(key)) == null) {
            return oldVal;
        }
        // strong CAS to minimize function call
        final V witness = caeTabAt(tab, i, null, newVal);
        if (witness == null) {
            this.addCount(1L);
            return newVal;
        }
        return witness;
    }
    @Override
    public V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remapping) {
        requireNonNull(key);
        requireNonNull(remapping);
        final int i = key.ordinal();
        for (final V[] tab = this.table;;) {
            final V oldVal = tabAt(tab, i);
            if (oldVal == null) {
                return null;
            }
            final V newVal = remapping.apply(key, oldVal);
            // strong CAS to minimize function call
            final V witness = caeTabAt(tab, i, oldVal, newVal);

            if (witness == oldVal) {
                if (newVal == null) {
                    this.addCount(-1L);
                }
                return newVal;
            } else if (witness == null) {
                return null;
            }
        }
    }
    @Override
    public V merge(final K key, final V value,
                   final BiFunction<? super V, ? super V, ? extends V> remapping) {
        requireNonNull(key);
        requireNonNull(value);
        requireNonNull(remapping);
        final int i = key.ordinal();
        for (final V[] tab = this.table;;) {
            final V oldVal = tabAt(tab, i);
            if (oldVal == null) {
                if (weakCasTabAt(tab, i, null, value)) {
                    this.addCount(1L);
                    return value;
                }
            } else {
                final V newVal = remapping.apply(oldVal, value);
                // strong CAS to minimize function call
                if (casTabAt(tab, i, oldVal, newVal)) {
                    if (newVal == null) {
                        this.addCount(-1L);
                    }
                    return newVal;
                }
            }
        }
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        if (this.checkKey(key)) {
            requireNonNull(value);
            final int i = ((Enum<?>) key).ordinal();
            for (final V[] tab = this.table;;) {
                final V v = tabAt(tab, i);
                if (Objects.equals(v, value)) {
                    final V witness = caeTabAt(tab, i, v, null);
                    if (witness == v) {
                        this.addCount(-1);
                        return true;
                    } else if (witness == null) {
                        return false;
                    }
                } else {
                    break;
                }
            }
        }
        return false;
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        requireNonNull(key);
        requireNonNull(oldValue);
        requireNonNull(newValue);
        final int i = key.ordinal();
        for (final V[] tab = this.table;;) {
            final V v = tabAt(tab, i);
            if (Objects.equals(v, oldValue)) {
                final V witness = caeTabAt(tab, i, v, newValue);
                if (witness == v) {
                    return true;
                } else if (witness == null) {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public V replace(final K key, final V value) {
        return this.put(key, value);
    }

    @Override
    public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> remapping) {
        requireNonNull(remapping);
        final K[] ks = this.keys; final V[] tab = this.table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            for (;;) {
                final V oldVal = tabAt(tab, i);
                if (oldVal == null) {
                    break;
                }
                final V newVal = requireNonNull(remapping.apply(ks[i], oldVal)),
                        witness = caeTabAt(tab, i, oldVal, newVal);
                if (witness == oldVal || witness == null) {
                    break;
                }
            }
        }
    }

    // the race that is here will not destroy anything for us
    @Override
    public Set<K> keySet() {
        final KeySetView<K,V> ks;
        if ((ks = this.keySet) != null) {
            return ks;
        }
        return this.keySet = new KeySetView<>(this);
    }

    @Override
    public Collection<V> values() {
        final ValuesView<K,V> vs;
        if ((vs = this.values) != null) {
            return vs;
        }
        return this.values = new ValuesView<>(this);
    }

    @Override
    public Set<Entry<K,V>> entrySet() {
        final EntrySetView<K,V> es;
        if ((es = this.entrySet) != null) {
            return es;
        }
        return this.entrySet = new EntrySetView<>(this);
    }

    /**
     * Helper method for EntrySetView.removeIf.
     */
    boolean removeEntryIf(final Predicate<? super Entry<K,V>> function) {
        requireNonNull(function);
        boolean removed = false;
        final K[] ks = this.keys; final V[] tab = this.table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            final V v = tabAt(tab, i);
            if (v == null) {
                continue;
            }
            final K k = ks[i];
            final Map.Entry<K,V> entry = Map.entry(k,v);
            if (function.test(entry) && this.remove(k,v)) {
                removed = true;
            }
        }
        return removed;
    }

    boolean removeValueIf(final Predicate<? super V> function) {
        requireNonNull(function);
        boolean removed = false;
        final K[] ks = this.keys; final V[] tab = this.table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            final V v = tabAt(tab, i);
            if (v != null &&
                    function.test(v) &&
                    this.remove(ks[i],v)) {
                removed = true;
            }
        }
        return removed;
    }

    /* --------------------- Views --------------------- */

    private static final class KeySetView<K extends Enum<K>,V>
            extends AbstractSet<K>
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 3978011019563538907L;
        private final ConcurrentEnumMap<K,V> map;

        KeySetView(final ConcurrentEnumMap<K,V> map) {
            this.map = map;
        }

        @Override
        public int size() {
            return this.map.size();
        }

        @Override
        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public void clear() {
            this.map.clear();
        }

        @Override
        public Iterator<K> iterator() {
            return new KeyIterator<>(this.map);
        }

        @Override
        public void forEach(final Consumer<? super K> action) {
            requireNonNull(action);
            this.map.forEach((k, v) -> action.accept(k));
        }

        @Override
        public boolean contains(final Object o) {
            return this.map.containsKey(o);
        }

        @Override
        public boolean remove(final Object o) {
            return this.map.remove(o) != null;
        }

        @Override
        public Spliterator<K> spliterator() {
            return Spliterators.spliterator(this,
                    Spliterator.DISTINCT |
                            Spliterator.NONNULL |
                            Spliterator.ORDERED |
                            Spliterator.CONCURRENT
            );
        }
    }

    private static final class ValuesView<K extends Enum<K>,V>
            extends AbstractCollection<V>
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 3274140860495273601L;
        private final ConcurrentEnumMap<? super K, V> map;

        ValuesView(final ConcurrentEnumMap<? super K, V> map) {
            this.map = map;
        }
        @Override
        public int size() {
            return this.map.size();
        }

        @Override
        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public void clear() {
            this.map.clear();
        }

        @Override
        public Iterator<V> iterator() {
            return new ValueIterator<>(this.map);
        }

        @Override
        public boolean contains(final Object o) {
            return this.map.containsValue(o);
        }

        @Override
        public void forEach(final Consumer<? super V> action) {
            requireNonNull(action);
            this.map.forEach((k, v) -> action.accept(v));
        }

        @Override
        public boolean removeIf(final Predicate<? super V> filter) {
            return this.map.removeValueIf(filter);
        }

        @Override
        public Spliterator<V> spliterator() {
            return Spliterators.spliterator(this,
                    Spliterator.NONNULL |
                            Spliterator.ORDERED |
                            Spliterator.CONCURRENT
            );
        }

        @Override
        public boolean remove(final Object o) {
            requireNonNull(o);
            final V[] tab = this.map.table;
            for (int i = 0, len = tab.length; i < len; ++i) {
                for (;;) {
                    final V val = tabAt(tab, i);
                    if (Objects.equals(val, o)) {
                        final V witness = caeTabAt(tab, i, val, null);
                        if (witness == val) {
                            this.map.addCount(-1L);
                            return true;
                        } else if (witness == null) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
            return false;
        }
    }
    private static final class EntrySetView<K extends Enum<K>,V>
            extends AbstractSet<Map.Entry<K,V>>
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 1663475809857555708L;
        private final ConcurrentEnumMap<K,V> map;

        EntrySetView(final ConcurrentEnumMap<K,V> map) {
            this.map = map;
        }
        @Override
        public int size() {
            return this.map.size();
        }

        @Override
        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public void clear() {
            this.map.clear();
        }

        @Override
        public Spliterator<Entry<K, V>> spliterator() {
            return Spliterators.spliterator(this,
                    Spliterator.DISTINCT |
                            Spliterator.NONNULL |
                            Spliterator.ORDERED |
                            Spliterator.CONCURRENT
            );
        }

        @Override
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator<>(this.map);
        }

        @Override
        public void forEach(final Consumer<? super Entry<K,V>> action) {
            requireNonNull(action);
            this.map.forEach((k, v) -> action.accept(Map.entry(k,v)));
        }

        @Override
        public boolean removeIf(final Predicate<? super Entry<K,V>> filter) {
            return this.map.removeEntryIf(filter);
        }

        @Override
        public boolean contains(final Object o) {
            final Object k;
            return o instanceof final Map.Entry<?,?> e &&
                    (k = e.getKey()) != null &&
                    Objects.equals(this.map.get(k), e.getValue());
        }

        @Override
        public boolean remove(final Object o) {
            final Object k; final Object v;
            return ((o instanceof final Map.Entry<?,?> e) &&
                    (k = e.getKey()) != null &&
                    (v = e.getValue()) != null &&
                    this.map.remove(k,v));
        }

        @Override
        public boolean add(final Entry<K,V> e) {
            return this.map.put(e.getKey(), e.getValue()) == null;
        }
    }
    private static final class KeyIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,K> {

        KeyIterator(final ConcurrentEnumMap<K,V> map) {
            super(map);
        }

        @Override
        public K next() {
            if (this.item == null) {
                throw new NoSuchElementException();
            }
            final int i = this.lastRet = this.index;
            this.advance();
            return this.map.keys[i];
        }
        @Override
        public void forEachRemaining(final Consumer<? super K> action) {
            requireNonNull(action);
            this.map.forEach((k, v) -> action.accept(k));
        }
    }

    private static final class ValueIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,V> {

        ValueIterator(final ConcurrentEnumMap<K,V> map) {
            super(map);
        }

        @Override
        public V next() {
            final V e = this.item;
            if (e == null) {
                throw new NoSuchElementException();
            }
            this.lastRet = this.index;
            this.advance();
            return e;
        }
        @Override
        public void forEachRemaining(final Consumer<? super V> action) {
            requireNonNull(action);
            this.map.forEach((k, v) -> action.accept(v));
        }
    }

    private static final class EntryIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,Map.Entry<K,V>> {

        EntryIterator(final ConcurrentEnumMap<K,V> map) {
            super(map);
        }
        @Override
        public Map.Entry<K,V> next() {
            final V e = this.item;
            if (e == null) {
                throw new NoSuchElementException();
            }
            final int i = this.lastRet = this.index;
            this.advance();
            return Map.entry(this.map.keys[i], e);
        }

        @Override
        public void forEachRemaining(final Consumer<? super Entry<K, V>> action) {
            requireNonNull(action);
            this.map.forEach((k, v) -> action.accept(Map.entry(k,v)));
        }
    }

    private abstract static sealed class EnumMapIterator<K extends Enum<K>,V,E>
            implements Iterator<E>
            permits KeyIterator, ValueIterator, EntryIterator {
        final ConcurrentEnumMap<K,V> map;
        V item;
        int index = -1, lastRet = -1;

        EnumMapIterator(final ConcurrentEnumMap<K,V> map) {
            this.map = map;
            this.advance();
        }

        void advance() {
            final V[] tab = this.map.table;
            final int len = tab.length;
            V e = null;

            int i = this.index;
            do {
                i++;
            } while (i < len && (e = tabAt(tab, i)) == null);
            this.index = i;
            this.item = e;

        }
        @Override
        public boolean hasNext() {
            return this.item != null;
        }

        @Override
        public void remove() {
            final int l = this.lastRet;
            if (l < 0) {
                throw new IllegalStateException();
            }
            final V[] tab = this.map.table;
            if (tabAt(tab, l) != null &&
                    getAndSetAt(tab, l, null) != null) {
                this.map.addCount(-1L);
            }
            this.lastRet = -1;
        }
    }

    @Override
    public int hashCode() {
        int h = 0;
        final K[] ks = this.keys; final V[] tab = this.table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            final V val;
            if ((val = tabAt(tab, i)) == null) {
                continue;
            }
            h += ks[i].hashCode() ^ val.hashCode();
        }
        return h;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof final Map<?,?> m) {
            final K[] ks = this.keys; final V[] tab = this.table;
            final int maxSize = ks.length;
            final int sz = m.size();
            if (sz > maxSize || sz != this.size()) {
                return false;
            }
            for (int i = 0, n = tab.length; i < n; ++i) {
                final V value = tabAt(tab, i);
                if (value != null &&
                        !value.equals(m.get(ks[i]))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Serial
    private void writeObject(final ObjectOutputStream s) throws IOException {
        s.writeObject(this.keyType);
        this.forEach((k, v) -> {
            try {
                s.writeObject(k);
                s.writeObject(v);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        });
        s.writeObject(null);
        s.writeObject(null);
    }
    @Serial
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        this.keyType = (Class<K>) s.readObject();
        this.keys = this.keyType.getEnumConstants();
        this.table = (V[]) new Object[this.keys.length];
        for (long delta = 0L;;) {
            final K k = (K) s.readObject();
            final V v = (V) s.readObject();
            if (k != null && v != null) {
                if (getAndSetAt(this.table, k.ordinal(), v) == null) {
                    ++delta;
                }
            } else {
                this.addCount(delta);
                return;
            }
        }
    }
    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner(
                ", ", "[", "]");
        this.forEach((k, v) -> joiner.add(k + "=" + v));
        return joiner.toString();
    }

    private boolean checkKey(final Object key) {
        requireNonNull(key);
        // Cheaper than instanceof Enum followed by getDeclaringClass
        final Class<?> keyClass = key.getClass();
        final Class<?> type = this.keyType;
        return keyClass == type || keyClass.getSuperclass() == type;
    }

    /*
     * Atomic access methods are used for an array
     */
    private static <V> V tabAt(final V[] tab, final int i) {
        return (V) AA.getAcquire(tab, i);
    }
    private static <V> boolean casTabAt(final V[] tab, final int i, final V c, final V v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    private static <V> boolean weakCasTabAt(final V[] tab, final int i, final V c, final V v) {
        return AA.weakCompareAndSet(tab, i, c, v);
    }

    private static <V> V caeTabAt(final V[] tab, final int i, final V c, final V v) {
        return (V) AA.compareAndExchange(tab, i, c, v);
    }

    private static <V> V getAndSetAt(final V[] tab, final int i, final V v) {
        return (V) AA.getAndSet(tab, i, v);
    }

    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle ADDER;

    static {
        try {
            final MethodHandles.Lookup l = MethodHandles.lookup();
            ADDER = l.findVarHandle(ConcurrentEnumMap.class, "counter",
                    LongAdder.class);
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
