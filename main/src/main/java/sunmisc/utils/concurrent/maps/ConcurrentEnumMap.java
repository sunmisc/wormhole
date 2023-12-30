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
public class ConcurrentEnumMap<K extends Enum<K>,V>
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

    public ConcurrentEnumMap(Class<? extends K> keyType) {
        this.keyType = keyType;
        this.keys = keyType.getEnumConstants();
        this.table = (V[]) new Object[keys.length];
    }
    public ConcurrentEnumMap(Map<? extends K, ? extends V> m) {
        this.keys = (K[]) m.keySet().toArray(Enum[]::new);
        this.keyType = keys[0].getDeclaringClass();
        this.table = (V[]) new Object[keyType.getEnumConstants().length];
        putAll(m);
    }

    private void addCount(long c) {
        if (c == 0L) return;
        LongAdder a = counter;
        if (a == null) {
            LongAdder newAdder = new LongAdder();

            if ((a = (LongAdder) ADDER.compareAndExchange(
                    this, null, newAdder)) == null)
                a = newAdder;
        }
        a.add(c);
    }

    @Override
    public V get(Object key) {
        return checkKey(key)
                ? tabAt(table, ((Enum<?>)key).ordinal())
                : null;
    }
    @Override
    public V put(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);

        int i = key.ordinal();
        final V prev = getAndSetAt(table, i, value);
        if (prev == null)
            addCount(1L);
        return prev;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        requireNonNull(m);

        long delta = 0L; V[] tab = table;
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            Enum<?> key = e.getKey(); Object val = e.getValue();
            if (getAndSetAt(tab, key.ordinal(), val) == null)
                ++delta;
        }
        addCount(delta);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        requireNonNull(action);

        K[] ks = keys; V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V v = tabAt(tab, i);
            if (v == null)
                continue;
            action.accept(ks[i], v);
        }
    }

    @Override
    public void clear() {
        long delta = 0L;
        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            if (tabAt(tab, i) != null &&
                    getAndSetAt(tab, i, null) != null)
                --delta;
        }
        addCount(delta);
    }

    @Override
    public V remove(Object key) {
        if (checkKey(key)) {
            int i = ((Enum<?>) key).ordinal();
            final V[] tab = table; V p = null;
            if (tabAt(tab, i) != null &&
                    (p = getAndSetAt(tab, i, null)) != null)
                addCount(-1L);
            return p;
        }
        return null;
    }

    @Override
    public int size() {
        LongAdder a = counter;
        if (a == null)
            return 0;
        // let's handle the overflow
        return Math.clamp(a.sum(), 0, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        final LongAdder a = counter;
        return a == null || a.sum() <= 0L;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        requireNonNull(value);

        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            if (Objects.equals(tabAt(tab, i), value))
                return true;
        }
        return false;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        requireNonNull(key);
        requireNonNull(value);

        int i = key.ordinal();
        V[] tab = table; V p;
        if ((p = tabAt(tab, i)) == null &&
                (p = caeTabAt(tab, i, null, value)) == null)
            addCount(1L);
        return p;
    }

    @Override
    public V compute(K key,
    BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        requireNonNull(key);
        requireNonNull(remappingFunction);

        int i = key.ordinal();
        V[] tab = table;
        for (V oldVal;;) {
            V newVal = remappingFunction.apply(key,
                    oldVal = tabAt(tab, i));
            // strong CAS to minimize function call
            if (casTabAt(tab, i, oldVal, newVal)) {
                addCount(oldVal == null ? 1L : newVal == null ? -1L : 0);
                return newVal;
            }
        }
    }
    @Override
    public V computeIfAbsent(K key,
    Function<? super K, ? extends V> mappingFunction) {
        requireNonNull(key);
        requireNonNull(mappingFunction);

        int i = key.ordinal();
        V[] tab = table;
        V oldVal = tabAt(tab, i), newVal;
        if (oldVal != null ||
                (newVal = mappingFunction.apply(key)) == null)
            return oldVal;
        // strong CAS to minimize function call
        var witness = caeTabAt(tab, i, null, newVal);
        if (witness == null) {
            addCount(1L);
            return newVal;
        }
        return witness;
    }
    @Override
    public V computeIfPresent(K key,
    BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        requireNonNull(key);
        requireNonNull(remappingFunction);

        int i = key.ordinal();
        V[] tab = table;
        for (V oldVal;;) {
            if ((oldVal = tabAt(tab, i)) == null)
                return null;
            V newVal = remappingFunction.apply(key, oldVal);
            // strong CAS to minimize function call
            var witness = caeTabAt(tab, i, oldVal, newVal);

            if (witness == oldVal) {
                if (newVal == null)
                    addCount(-1L);
                return newVal;
            } else if (witness == null)
                return null;
        }
    }
    @Override
    public V merge(K key, V value,
    BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        requireNonNull(key);
        requireNonNull(value);
        requireNonNull(remappingFunction);

        int i = key.ordinal();
        V[] tab = table;
        for (V oldVal;;) {
            if ((oldVal = tabAt(tab, i)) == null) {
                if (weakCasTabAt(tab, i, null, value)) {
                    addCount(1L);
                    return value;
                }
            } else {
                V newVal = remappingFunction.apply(oldVal, value);
                // strong CAS to minimize function call
                if (casTabAt(tab, i, oldVal, newVal)) {
                    if (newVal == null)
                        addCount(-1L);
                    return newVal;
                }
            }
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        requireNonNull(value);

        if (checkKey(key)) {
            final int i = ((Enum<?>) key).ordinal();
            for (V[] tab = table;;) {
                V v = tabAt(tab, i);
                if (Objects.equals(v, value)) {
                    var witness = caeTabAt(tab, i, v, null);
                    if (witness == v) {
                        addCount(-1);
                        return true;
                    } else if (witness == null)
                        return false;
                } else
                    break;
            }
        }
        return false;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        requireNonNull(key);
        requireNonNull(oldValue);
        requireNonNull(newValue);

        final int i = key.ordinal();
        for (V[] tab = table;;) {
            V v = tabAt(tab, i);
            if (Objects.equals(v, oldValue)) {
                var witness = caeTabAt(tab, i, v, newValue);
                if (witness == v)
                    return true;
                else if (witness == null)
                    return false;
            } else
                return false;
        }
    }

    @Override
    public V replace(K key, V value) {
        return put(key, value);
    }

    @Override
    public void
    replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        requireNonNull(function);

        K[] ks = keys; V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            for (V oldVal;;) {
                if ((oldVal = tabAt(tab, i)) == null)
                    break;
                V newVal = requireNonNull(function.apply(ks[i], oldVal));

                var witness = caeTabAt(tab, i, oldVal, newVal);

                if (witness == oldVal || witness == null)
                    break;
            }
        }
    }

    // the race that is here will not destroy anything for us
    @Override
    public Set<K> keySet() {
        KeySetView<K,V> ks;
        if ((ks = keySet) != null) return ks;
        return keySet = new KeySetView<>(this);
    }

    @Override
    public Collection<V> values() {
        ValuesView<K,V> vs;
        if ((vs = values) != null) return vs;
        return values = new ValuesView<>(this);
    }

    @Override
    public Set<Entry<K,V>> entrySet() {
        EntrySetView<K,V> es;
        if ((es = entrySet) != null) return es;
        return entrySet = new EntrySetView<>(this);
    }
    /**
     * Helper method for EntrySetView.removeIf.
     */
    boolean removeEntryIf(Predicate<? super Entry<K,V>> function) {
        requireNonNull(function);

        boolean removed = false;
        K[] ks = keys; V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V v = tabAt(tab, i);
            if (v == null)
                continue;
            K k = ks[i];
            Map.Entry<K,V> entry = Map.entry(k,v);

            if (function.test(entry) && remove(k,v))
                removed = true;
        }
        return removed;
    }

    boolean removeValueIf(Predicate<? super V> function) {
        requireNonNull(function);

        boolean removed = false;
        K[] ks = keys; V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V v = tabAt(tab, i);
            if (v != null &&
                    function.test(v) &&
                    remove(ks[i],v))
                removed = true;
        }
        return removed;
    }

    /* --------------------- Views --------------------- */

    private static final class KeySetView<K extends Enum<K>,V>
            extends AbstractSet<K>
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 3978011019563538907L;
        final ConcurrentEnumMap<K,V> map;

        KeySetView(ConcurrentEnumMap<K,V> map) {
            this.map = map;
        }
        @Override public int size() { return map.size(); }
        @Override public boolean isEmpty() { return map.isEmpty(); }
        @Override public void clear() { map.clear(); }

        @Override public Iterator<K>
        iterator() { return new KeyIterator<>(map); }

        @Override
        public void forEach(Consumer<? super K> action) {
            requireNonNull(action);
            map.forEach((k,v) -> action.accept(k));
        }

        @Override public boolean
        contains(Object o) { return map.containsKey(o); }

        @Override public boolean
        remove(Object o) { return map.remove(o) != null; }

        @Override
        public Spliterator<K> spliterator() {
            return Spliterators.spliterator(this,
                    Spliterator.DISTINCT |
                    Spliterator.NONNULL |
                    Spliterator.ORDERED |
                    Spliterator.CONCURRENT);
        }

    }

    private static final class ValuesView<K extends Enum<K>,V>
            extends AbstractCollection<V>
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 3274140860495273601L;
        final ConcurrentEnumMap<? super K, V> map;

        ValuesView(ConcurrentEnumMap<? super K, V> map) {
            this.map = map;
        }
        @Override public int size() { return map.size(); }
        @Override public boolean isEmpty() { return map.isEmpty(); }
        @Override public void clear() { map.clear(); }


        @Override public Iterator<V>
        iterator() { return new ValueIterator<>(map); }

        @Override public boolean
        contains(Object o) { return map.containsValue(o); }

        @Override
        public void forEach(Consumer<? super V> action) {
            requireNonNull(action);
            map.forEach((k,v) -> action.accept(v));
        }

        @Override
        public boolean
        removeIf(Predicate<? super V> filter) {
            return map.removeValueIf(filter);
        }

        @Override
        public Spliterator<V> spliterator() {
            return Spliterators.spliterator(this,
                    Spliterator.NONNULL |
                    Spliterator.ORDERED |
                    Spliterator.CONCURRENT);
        }

        @Override
        public boolean remove(Object o) {
            requireNonNull(o);

            V[] tab = map.table;
            for (int i = 0, len = tab.length; i < len; ++i) {
                for (;;) {
                    V val = tabAt(tab, i);
                    if (Objects.equals(val, o)) {
                        var witness = caeTabAt(tab, i, val, null);
                        if (witness == val) {
                            map.addCount(-1L);
                            return true;
                        } else if (witness == null)
                            break;
                    } else
                        break;
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
        final ConcurrentEnumMap<K,V> map;

        EntrySetView(ConcurrentEnumMap<K,V> map) {
            this.map = map;
        }
        @Override public int size() { return map.size(); }
        @Override public boolean isEmpty() { return map.isEmpty(); }
        @Override public void clear() { map.clear(); }

        @Override
        public Spliterator<Entry<K, V>> spliterator() {
            return Spliterators.spliterator(this,
                    Spliterator.DISTINCT |
                    Spliterator.NONNULL |
                    Spliterator.ORDERED |
                    Spliterator.CONCURRENT);
        }

        @Override public Iterator<Map.Entry<K,V>>
        iterator() { return new EntryIterator<>(map); }

        @Override
        public void forEach(Consumer<? super Entry<K,V>> action) {
            requireNonNull(action);
            map.forEach((k,v) -> action.accept(new MapEntry<>(map, k,v)));
        }

        @Override
        public boolean removeIf(Predicate<? super Entry<K,V>> filter) {
            return map.removeEntryIf(filter);
        }

        @Override
        public boolean contains(Object o) {
            Object k;
            return o instanceof Map.Entry<?,?> e &&
                    (k = e.getKey()) != null &&
                    Objects.equals(map.get(k), e.getValue());
        }
        @Override
        public boolean remove(Object o) {
            Object k; Object v;
            return ((o instanceof Map.Entry<?,?> e) &&
                    (k = e.getKey()) != null &&
                    (v = e.getValue()) != null &&
                    map.remove(k,v));
        }
        @Override
        public boolean add(Entry<K,V> e) {
            return map.put(e.getKey(), e.getValue()) == null;
        }
    }
    private static final class KeyIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,K> {
        KeyIterator(ConcurrentEnumMap<K,V> map) {
            super(map);
        }

        @Override
        public K next() {
            if (item == null)
                throw new NoSuchElementException();
            int i = lastRet = index;
            advance();
            return map.keys[i];
        }
        @Override
        public void forEachRemaining(Consumer<? super K> action) {
            requireNonNull(action);
            map.forEach((k,v) -> action.accept(k));
        }
    }

    private static final class ValueIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,V> {
        ValueIterator(ConcurrentEnumMap<K,V> map) {
            super(map);
        }

        @Override
        public V next() {
            V e = item;
            if (e == null)
                throw new NoSuchElementException();
            lastRet = index;
            advance();
            return e;
        }
        @Override
        public void forEachRemaining(Consumer<? super V> action) {
            requireNonNull(action);
            map.forEach((k,v) -> action.accept(v));
        }
    }

    private static final class EntryIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,Map.Entry<K,V>> {
        EntryIterator(ConcurrentEnumMap<K,V> map) {
            super(map);
        }
        @Override
        public Map.Entry<K,V> next() {
            V e = item;
            if (e == null)
                throw new NoSuchElementException();
            int i = lastRet = index;
            advance();
            return new MapEntry<>(map, map.keys[i], e);
        }

        @Override
        public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
            requireNonNull(action);
            map.forEach((k,v) -> action.accept(new MapEntry<>(map, k,v)));
        }
    }

    private abstract static sealed class EnumMapIterator<K extends Enum<K>,V,E>
            implements Iterator<E>
            permits KeyIterator, ValueIterator, EntryIterator {
        final ConcurrentEnumMap<K,V> map;
        V item;
        int index = -1, lastRet = -1;

        EnumMapIterator(ConcurrentEnumMap<K,V> map) {
            this.map = map;
            advance();
        }

        void advance() {
            final V[] tab = map.table;
            final int len = tab.length;
            V e = null;

            int i = index;
            do {
                i++;
            } while (i < len && (e = tabAt(tab, i)) == null);
            index = i;
            item = e;

        }
        @Override
        public boolean hasNext() {
            return item != null;
        }

        @Override
        public void remove() {
            final int l = lastRet;
            if (l < 0)
                throw new IllegalStateException();
            final V[] tab = map.table;
            if (tabAt(tab, l) != null &&
                    getAndSetAt(tab, l, null) != null)
                map.addCount(-1L);
            lastRet = -1;
        }
    }

    private static final class MapEntry<K extends Enum<K>,V>
            implements Map.Entry<K,V> {
        private final ConcurrentEnumMap<? super K, ? super V> map;
        private final K key; // non-null
        private       V val; // non-null

        MapEntry(ConcurrentEnumMap<? super K, ? super V> map, K key, V val) {
            this.map = map;
            this.key = key;
            this.val = val;
        }

        @Override public K getKey() { return key; }
        @Override public V getValue() { return val; }
        @Override public int hashCode() { return key.hashCode() ^ val.hashCode(); }
        @Override public String toString() { return key + "=" + val; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Map.Entry<?,?> e
                    && key.equals(e.getKey())
                    && val.equals(e.getValue());
        }

        @Override
        public V setValue(V value) {
            requireNonNull(value);
            V oldVal = val;
            val = value;
            map.put(key, value);
            return oldVal;
        }
    }

    @Override
    public int hashCode() {
        int h = 0;
        K[] ks = keys; V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V val;
            if ((val = tabAt(tab, i)) == null)
                continue;
            h += ks[i].hashCode() ^ val.hashCode();
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        else if (o instanceof Map<?,?> m) {
            K[] ks = keys; V[] tab = table;
            int maxSize = ks.length, sz = m.size();
            if (sz > maxSize || sz != size())
                return false;
            for (int i = 0, n = tab.length; i < n; ++i) {
                V value = tabAt(tab, i);
                if (value != null &&
                        !value.equals(m.get(ks[i])))
                    return false;
            }
            return true;
        }
        return false;
    }

    @Serial
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.writeObject(keyType);
        forEach((k,v) -> {
            try {
                s.writeObject(k);
                s.writeObject(v);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        s.writeObject(null);
        s.writeObject(null);
    }
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        this.keyType = (Class<K>) s.readObject();
        this.keys = keyType.getEnumConstants();
        this.table = (V[]) new Object[keys.length];
        for (long delta = 0L;;) {
            K k = (K) s.readObject();
            V v = (V) s.readObject();
            if (k != null && v != null) {
                if (getAndSetAt(table, k.ordinal(), v) == null)
                    ++delta;
            } else {
                addCount(delta);
                return;
            }
        }
    }
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(
                ", ", "[", "]");
        forEach((k,v) -> joiner.add(k + "=" + v));
        return joiner.toString();
    }

    private boolean checkKey(Object key) {
        requireNonNull(key);
        // Cheaper than instanceof Enum followed by getDeclaringClass
        Class<?> keyClass = key.getClass(), type = keyType;
        return keyClass == type || keyClass.getSuperclass() == type;
    }

    /*
     * Atomic access methods are used for an array
     */
    private static <V> V tabAt(V[] tab, int i) {
        return (V) AA.getAcquire(tab, i);
    }
    private static <V> boolean casTabAt(V[] tab, int i, V c, V v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    private static <V> boolean weakCasTabAt(V[] tab, int i, V c, V v) {
        return AA.weakCompareAndSet(tab, i, c, v);
    }

    private static <V> V caeTabAt(V[] tab, int i, V c, V v) {
        return (V) AA.compareAndExchange(tab, i, c, v);
    }

    private static <V> V getAndSetAt(V[] tab, int i, V v) {
        return (V) AA.getAndSet(tab, i, v);
    }

    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle ADDER;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ADDER = l.findVarHandle(ConcurrentEnumMap.class, "counter",
                    LongAdder.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
