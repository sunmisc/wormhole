package zelva.utils.concurrent;

import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A specialized implementation of the map for use with enumeration type keys.
 * All keys in the enumeration map must belong to the same enumeration type,
 * which is explicitly or implicitly specified when creating the map.
 * Enumeration maps are internally represented as arrays.
 * This view is extremely compact and efficient.
 * Enumeration maps are maintained in the natural order of their keys
 * (the order in which enumeration constants are declared).
 * This is reflected in the iterators returned by the collection views
 * (keySet (), entrySet() and values ()).
 * The iterators returned by the collection views are poorly consistent:
 * they will never throw a ConcurrentModificationException
 * and may or may not show the effects of any map changes,
 * which occur during the execution of the iteration.
 * Null keys are not allowed.
 * Attempts to insert a null key will cause a NullPointerException.
 * However, attempts to check for the presence of a null key or delete it will work properly.
 * Zero values are allowed.
 * This map differs from EnumMap in that it is thread-safe
 * and scales well
 *
 * @author ZelvaLea
 *
 * Type parameters:
 * <K> – the type of keys maintained by this map
 * <V> – the type of mapped values
 */

public final class ConcurrentEnumMap<K extends Enum<K>,V>
        implements ConcurrentMap<K,V> {
    // An object of the class for the enumeration type of all the keys of this map
    private final Class<K> keyType;
    // element count
    private final LongAdder counter;
    // All the values comprising K
    private final K[] keys;
    // Array representation of this map. The ith element is the value to which universe[i]
    private final V[] table;

    // views
    private KeySetView<K,V> keySet;
    private ValuesView<K,V> values;
    private EntrySetView<K,V> entrySet;

    public ConcurrentEnumMap(Class<K> keyType) {
        this.keyType = keyType;
        this.keys = keyType.getEnumConstants();
        this.counter = new LongAdder();
        this.table = (V[]) new Object[keys.length];
    }
    public ConcurrentEnumMap(Map<K, ? extends V> m) {
        if (m instanceof ConcurrentEnumMap) {
            ConcurrentEnumMap<K,V> em = (ConcurrentEnumMap<K,V>)m;
            this.keys = em.keys;
            this.keyType = em.keyType;
            this.table = em.table.clone();
            this.counter = em.counter;
        } else {
            if (m.isEmpty())
                throw new IllegalArgumentException("Specified map is empty");
            this.keys = (K[]) m.keySet().toArray(Enum[]::new);
            this.keyType = keys[0].getDeclaringClass();
            this.table = (V[]) new Object[keyType.getEnumConstants().length];
            this.counter = new LongAdder();
            putAll(m);
        }
    }
    @SuppressWarnings("unchecked")
    private static <V> V tabAt(V[] tab, int i) {
        return (V) AA.getAcquire(tab, i);
    }
    private static <V> boolean casTabAt(V[] tab, int i, V c, V v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    private static <V> boolean weakCasTabAt(V[] tab, int i, V c, V v) {
        return AA.weakCompareAndSet(tab, i, c, v);
    }
    @SuppressWarnings("unchecked")
    private static <V> V caeTabAt(V[] tab, int i, V c, V v) {
        return (V) AA.compareAndExchange(tab, i, c, v);
    }
    @SuppressWarnings("unchecked")
    private static <V> V getAndSetAt(V[] tab, int i, V v) {
        return (V) AA.getAndSet(tab, i, v);
    }

    private void addCount(long c) {
        if (c == 0L) return;
        counter.add(c);
    }

    @Override
    public V get(Object key) {
        return isValidKey(key)
                ? tabAt(table, ((Enum<?>)key).ordinal())
                : null;
    }
    @Override
    public V put(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();
        int i = key.ordinal();
        final V prev = getAndSetAt(table, i, value);
        if (prev == null)
            addCount(1L);
        return prev;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        long delta = 0L;
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            Enum<?> key = e.getKey(); Object val = e.getValue();
            if (getAndSetAt(table, key.ordinal(), val) == null)
                ++delta;
        }
        addCount(delta);
    }
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null)
            throw new NullPointerException();
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
                    getAndSetAt(tab, i, null) != null) {
                --delta;
            }
        }
        addCount(delta);
    }

    @Override
    public V remove(Object key) {
        if (isValidKey(key)) {
            int i = ((Enum<?>) key).ordinal();
            final V[] tab = table; V p = null;
            if (tabAt(tab, i) != null &&
                    (p = getAndSetAt(tab, i, null)) != null) {
                addCount(-1L);
            }
            return p;
        }
        return null;
    }

    @Override
    public int size() {
        // let's handle the overflow
        long n = Math.max(counter.sum(), 0L);
        return n >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
    }

    @Override
    public boolean isEmpty() {
        return counter.sum() <= 0L;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null)
            throw new NullPointerException();
        V[] tab = table;
        for (int i = 0, len = tab.length; i < len; ++i) {
            if (Objects.equals(tabAt(tab, i), value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException();
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
        if (key == null || remappingFunction == null)
            throw new NullPointerException();
        int i = key.ordinal();
        V[] tab = table;
        for (V prev;;) {
            V newVal = remappingFunction.apply(key, prev = tabAt(tab, i));
            if (newVal == null && prev == null) {
                return null;
                // strong CAS to minimize function call
            } else if (casTabAt(tab, i, prev, newVal)) {
                addCount(prev == null ? 1L : newVal == null ? -1L : 0);
                return newVal;
            }
        }
    }
    @Override
    public V computeIfAbsent(K key,
                             Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null)
            throw new NullPointerException();
        int i = key.ordinal();
        V[] tab = table;
        for (V prev, newVal;;) {
            if ((prev = tabAt(tab, i)) != null ||
                    (newVal = mappingFunction.apply(key)) == null) {
                return prev;
                // strong CAS to minimize function call
            } else if (casTabAt(tab, i, null, newVal)) {
                addCount(1L);
                return newVal;
            }
        }
    }
    @Override
    public V computeIfPresent(K key,
                              BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null)
            throw new NullPointerException();
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
    public V merge(K key, V value,
                   BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null)
            throw new NullPointerException();
        int i = key.ordinal();
        V[] tab = table;
        for (V prev;;) {
            if ((prev = tabAt(tab, i)) == null) {
                if (weakCasTabAt(tab, i, null, value)) {
                    addCount(1L);
                    return value;
                } else {
                    continue;
                }
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
    public boolean remove(Object key, Object value) {
        if (value == null)
            throw new NullPointerException();
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
    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null)
            throw new NullPointerException();
        int i = key.ordinal();
        V[] tab = table;
        return tabAt(tab, i) == oldValue &&
                casTabAt(tab, i, oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
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
        @Override public int size() {return map.size();}
        @Override public boolean isEmpty() {return map.isEmpty();}
        @Override public void clear() {map.clear();}

        @Override public Iterator<K> iterator() {return new KeyIterator<>(map);}
        @Override public boolean contains(Object o) {return map.containsKey(o);}
        @Override public boolean remove(Object o) {return map.remove(o) != null;}
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
        @Override public int size() {return map.size();}
        @Override public boolean isEmpty() {return map.isEmpty();}
        @Override public void clear() {map.clear();}

        @Override public Iterator<V> iterator() {return new ValueIterator<>(map);}

        @Override public boolean contains(Object o) {return map.containsValue(o);}

        @Override
        public boolean remove(Object o) {
            if (o == null)
                throw new NullPointerException();
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
        @Override public int size() {return map.size();}
        @Override public boolean isEmpty() {return map.isEmpty();}
        @Override public void clear() {map.clear();}

        @Override public Iterator<Map.Entry<K,V>> iterator() {return new EntryIterator<>(map);}

        @Override
        public boolean contains(Object o) {
            Object k, v, r; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (r = map.get(k)) != null &&
                    (v = e.getValue()) != null &&
                    (v == r || v.equals(r)));
        }
        @Override
        public boolean remove(Object o) {
            Object k, v; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
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
                    map.keys[i], tabAt(map.table, i),
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
                    getAndSetAt(tab, l, null) != null) {
                map.addCount(-1L);
            }
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

        @Override public K getKey() {return key;}
        @Override public V getValue() {return val;}
        @Override public int hashCode() {return key.hashCode() ^ val.hashCode();}
        @Override public String toString() {return key.toString() + ' ' + val.toString();}

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
            if (value == null) throw new NullPointerException();
            V v = val;
            val = value;
            map.put(key, value);
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
            if ((v1 = m.get(keys[i])) == null) {
                return false;
            } else if (v1 != (v2 = tabAt(tab, i)) && !v1.equals(v2)) {
                return false;
            }
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

    private boolean isValidKey(Object key) {
        if (key == null)
            return false;
        // Cheaper than instanceof Enum followed by getDeclaringClass
        Class<?> keyClass = key.getClass();
        return keyClass == keyType || keyClass.getSuperclass() == keyType;
    }

    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
}
