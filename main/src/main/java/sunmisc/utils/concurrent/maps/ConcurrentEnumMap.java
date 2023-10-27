package sunmisc.utils.concurrent.maps;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A specialized implementation of the map for use with enumeration type keys.
 * <p>All keys in the enumeration map must belong to the same enumeration type,
 * which is explicitly or implicitly specified when creating the map.
 * Enumeration maps are internally represented as arrays.
 * <p>This view is extremely compact and efficient.
 * <p>Enumeration maps are maintained in the natural order of their keys
 * (the order in which enumeration constants are declared).
 * <p>This is reflected in the iterators returned by the collection views
 * (keySet (), entrySet() and values ()).
 * <p>The iterators returned by the collection views are poorly consistent:
 * <p>they will never throw a ConcurrentModificationException
 * and may or may not show the effects of any map changes,
 * which occur during the execution of the iteration.</p>
 * <p>Null keys are not allowed.
 * Zero values are allowed.
 * <p>Attempts to insert a null key will cause a NullPointerException.
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
    // An object of the class for the enumeration type of all the keys of this map
    private transient Class<? extends K> keyType;
    // element count
    private transient LongAdder counter;
    // All the values comprising K
    private transient K[] keys;
    // Array representation of this map. The ith element is the value to which universe[i]
    private transient V[] table;

    // views

    // todo: delete the field,
    //  create a new object for each call,
    //  but ValueBased (hello Valhalla)

    private transient KeySetView<K,V> keySet;
    private transient ValuesView<K,V> values;
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
        return isValidKey(key)
                ? tabAt(table, ((Enum<?>)key).ordinal())
                : null;
    }
    @Override
    public V put(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        int i = key.ordinal();
        final V prev = getAndSetAt(table, i, value);
        if (prev == null)
            addCount(1L);
        return prev;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
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
        Objects.requireNonNull(action);
        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V v = tabAt(tab, i);
            if (v == null)
                continue;
            action.accept(keys[i], v);
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
        if (isValidKey(key)) {
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
        LongAdder a = counter;
        return a == null || a.sum() <= 0L;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        Objects.requireNonNull(value);
        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            if (Objects.equals(tabAt(tab, i), value))
                return true;
        }
        return false;
    }

    @Override
    public V putIfAbsent(@NotNull K key, V value) {
        Objects.requireNonNull(value);

        int i = key.ordinal();
        V[] tab = table; V p;
        if ((p = tabAt(tab, i)) == null &&
                (p = caeTabAt(tab, i, null, value)) == null)
            addCount(1L);
        return p;
    }

    @Override
    public V compute(K key, @NotNull
    BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key);
        int i = key.ordinal();
        V[] tab = table;
        for (V prev;;) {
            V newVal = remappingFunction.apply(key, prev = tabAt(tab, i));
            if (newVal == null && prev == null)
                return null;
                // strong CAS to minimize function call
            else if (casTabAt(tab, i, prev, newVal)) {
                addCount(prev == null ? 1L : newVal == null ? -1L : 0);
                return newVal;
            }
        }
    }
    @Override
    public V computeIfAbsent(K key, @NotNull
    Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(key);

        int i = key.ordinal();
        V[] tab = table;
        for (V prev, newVal;;) {
            if ((prev = tabAt(tab, i)) != null ||
                    (newVal = mappingFunction.apply(key)) == null)
                return prev;
                // strong CAS to minimize function call
            else if (casTabAt(tab, i, null, newVal)) {
                addCount(1L);
                return newVal;
            }
        }
    }
    @Override
    public V computeIfPresent(K key, @NotNull
    BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key);
        int i = key.ordinal();
        V[] tab = table;
        for (V prev;;) {
            if ((prev = tabAt(tab, i)) == null)
                return null;
            V newVal = remappingFunction.apply(key, prev);
            // strong CAS to minimize function call
            if (casTabAt(tab, i, prev, newVal)) {
                if (newVal == null)
                    addCount(-1L);
                return newVal;
            }
        }
    }
    @Override
    public V merge(K key, @NotNull V value, @NotNull
    BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key);
        int i = key.ordinal();
        V[] tab = table;
        for (V prev;;) {
            if ((prev = tabAt(tab, i)) == null) {
                if (weakCasTabAt(tab, i, null, value)) {
                    addCount(1L);
                    return value;
                } else
                    continue;
            }
            V newVal = remappingFunction.apply(prev, value);
            if (casTabAt(tab, i, prev, newVal)) {
                if (newVal == null)
                    addCount(-1L);
                return newVal;
            }
        }
    }

    @Override
    public boolean remove(@NotNull Object key, Object value) {
        Objects.requireNonNull(value);
        if (isValidKey(key)) {
            int i = ((Enum<?>) key).ordinal();
            V[] tab = table;
            if (tabAt(tab, i) == value &&
                    casTabAt(tab, i, value, null)) {
                addCount(-1);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean replace(@NotNull K key,
                           @NotNull V oldValue,
                           @NotNull V newValue) {
        int i = key.ordinal();
        V[] tab = table;
        return tabAt(tab, i) == oldValue &&
                casTabAt(tab, i, oldValue, newValue);
    }

    @Override
    public V replace(@NotNull K key, @NotNull V value) {
        return put(key, value);
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
        Objects.requireNonNull(function);
        V[] tab = table; boolean removed = false;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V v = tabAt(tab, i);
            if (v == null)
                continue;
            K k = keys[i];
            Map.Entry<K,V> entry = Map.entry(k,v);

            if (function.test(entry) && remove(k,v))
                removed = true;
        }
        return removed;
    }

    /**
     * Helper method for ValuesView.removeIf.
     */
    boolean removeValueIf(Predicate<? super V> function) {
        Objects.requireNonNull(function);
        boolean removed = false;
        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V v = tabAt(tab, i);
            if (v != null &&
                    function.test(v) &&
                    remove(keys[i],v))
                removed = true;
        }
        return removed;
    }

    /* --------------------- Views --------------------- */

    static final class KeySetView<K extends Enum<K>,V>
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

        @Override public @NotNull
        Iterator<K> iterator() { return new KeyIterator<>(map); }
        @Override public boolean contains(Object o) { return map.containsKey(o); }
        @Override public boolean remove(Object o) { return map.remove(o) != null; }
    }

    static final class ValuesView<K extends Enum<K>,V>
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


        @Override public @NotNull
        Iterator<V> iterator() { return new ValueIterator<>(map); }

        @Override public boolean contains(Object o) { return map.containsValue(o); }

        @Override
        public boolean removeIf(Predicate<? super V> filter) {
            return map.removeValueIf(filter);
        }

        @Override
        public boolean remove(Object o) {
            Objects.requireNonNull(o);
            V[] tab = map.table;
            for (int i = 0, len = tab.length; i < len; ++i) {
                if (tabAt(tab, i) == o &&
                        casTabAt(tab, i, o, null)) {
                    map.addCount(-1L);
                    return true;
                }
            }
            return false;
        }
    }
    static final class EntrySetView<K extends Enum<K>,V>
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

        @Override public @NotNull
        Iterator<Map.Entry<K,V>> iterator() { return new EntryIterator<>(map); }

        @Override
        public boolean removeIf(Predicate<? super Entry<K,V>> filter) {
            return map.removeEntryIf(filter);
        }

        @Override
        public boolean contains(Object o) {
            Map.Entry<K,V> e;
            K k; V v, r;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<K,V>)o).getKey()) != null &&
                    (r = map.get(k)) != null &&
                    (v = e.getValue()) != null &&
                    (v == r || v.equals(r)));
        }
        @Override
        public boolean remove(Object o) {
            Map.Entry<K,V> e;
            K k; V v;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<K,V>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    map.remove(k,v));
        }
        @Override
        public boolean add(Entry<K,V> e) {
            return map.put(e.getKey(), e.getValue()) == null;
        }
    }
    static final class KeyIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,K> {
        KeyIterator(ConcurrentEnumMap<K,V> map) {
            super(map);
        }
        @Override
        public K next() {
            if (!hasNext())
                throw new NoSuchElementException();
            return map.keys[lastReturnedIndex = index++];
        }
    }

    static final class ValueIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,V> {
        ValueIterator(ConcurrentEnumMap<K,V> map) {
            super(map);
        }

        @Override
        public V next() {
            if (!hasNext())
                throw new NoSuchElementException();
            return tabAt(map.table, lastReturnedIndex = index++);
        }
    }

    static final class EntryIterator<K extends Enum<K>,V>
            extends EnumMapIterator<K,V,Map.Entry<K,V>> {
        EntryIterator(ConcurrentEnumMap<K,V> map) {
            super(map);
        }
        @Override
        public Map.Entry<K,V> next() {
            if (!hasNext())
                throw new NoSuchElementException();
            int i = lastReturnedIndex = index++;
            return new MapEntry<>(
                    map.keys[i],
                    tabAt(map.table, i),
                    map);
        }
    }

    abstract static sealed class EnumMapIterator<K extends Enum<K>,V,E>
            implements Iterator<E>
            permits KeyIterator, ValueIterator, EntryIterator {
        final ConcurrentEnumMap<K,V> map;
        int index, lastReturnedIndex = -1;

        EnumMapIterator(ConcurrentEnumMap<K,V> map) {
            this.map = map;
        }

        @Override
        public boolean hasNext() {
            final V[] tab = map.table;
            final int len = tab.length;
            while (index < len && tabAt(tab, index) == null)
                index++;
            return index != len;
        }

        @Override
        public void remove() {
            final int l = lastReturnedIndex;
            if (l < 0)
                throw new IllegalStateException();
            final V[] tab = map.table;
            if (tabAt(tab, l) != null &&
                    getAndSetAt(tab, l, null) != null)
                map.addCount(-1L);
            lastReturnedIndex = -1;
        }
    }

    static final class MapEntry<K extends Enum<K>,V>
            implements Map.Entry<K,V> {
        final K key; // non-null
        V val;       // non-null
        final ConcurrentEnumMap<? super K, ? super V> map;
        MapEntry(K key, V val, ConcurrentEnumMap<? super K, ? super V> map) {
            this.key = key;
            this.val = val;
            this.map = map;
        }

        @Override public K getKey() { return key; }
        @Override public V getValue() { return val; }
        @Override public int hashCode() { return key.hashCode() ^ val.hashCode(); }
        @Override public String toString() { return key.toString() + ' ' + val.toString(); }

        @Override
        public boolean equals(Object o) {
            Object k, v, v1; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    (k == key || k.equals(key)) &&
                    (v == (v1 = val) || v.equals(v1)));
        }

        @Override
        public V setValue(V value) {
            Objects.requireNonNull(value);
            V v = val;
            val = value;
            //Object o =
            map.put(key, value);
            //assert o == v;
            return v;
        }
    }

    @Override
    public int hashCode() {
        int h = 0;
        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            V val;
            if ((val = tabAt(tab, i)) == null)
                continue;
            h += keys[i].hashCode() ^ val.hashCode();
        }
        return h;
    }
    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Map<?,?> m))
            return false;
        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            Object v1,v2;
            if ((v1 = m.get(keys[i])) == null)
                return false;
            else if (v1 != (v2 = tabAt(tab, i)) && !v1.equals(v2))
                return false;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            Object mk, mv, v;
            if ((mk = e.getKey()) == null ||
                    (mv = e.getValue()) == null ||
                    (v = get(mk)) == null ||
                    (mv != v && !mv.equals(v)))
                return false;
        }
        return true;
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
        forEach((k,v) -> joiner.add(k.toString() + '=' + v.toString()));
        return joiner.toString();
    }

    private boolean isValidKey(Object key) {
        if (key == null)
            return false;
        // Cheaper than instanceof Enum followed by getDeclaringClass
        Class<?> keyClass = key.getClass();
        return keyClass == keyType || keyClass.getSuperclass() == keyType;
    }

    /*
     * Atomic access methods are used for array
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
