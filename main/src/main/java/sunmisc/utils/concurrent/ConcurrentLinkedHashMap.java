package sunmisc.utils.concurrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

class ConcurrentLinkedHashMap<K,V> extends AbstractMap<K,V>
        implements ConcurrentMap<K,V> {

    static final int MAXIMUM_CAPACITY = 1 << 30;
    private volatile PointerNode<K,V> last;
    private final AtomicInteger size = new AtomicInteger();

    // todo: UnblockingArrayBuffer
    private volatile PointerNode<K,V>[] table = new PointerNode[8];

    public ConcurrentLinkedHashMap() {
        last = new PointerNode<>(null);
    }

    static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    static int spread(int h) {
        return (h ^ (h >>> 16));
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public V get(Object key) {
        int h = spread(key.hashCode());
        PointerNode<K,V>[] tab = table;
        int i = h & (tab.length - 1);
        PointerNode<K,V> n = tabAt(tab, i);
        Node<K,V> p;
        return (p = n.find(h, key)) != null ? p.val : null;
    }

    public static void main(String[] args) {
        ConcurrentLinkedHashMap<Integer,Integer> map
                = new ConcurrentLinkedHashMap<>();
        map.put(34, 1);
        map.put(23, 1);
        map.put(1, 1);
        map.put(66, 1);

        map.forEach(x -> {
            System.out.println(x.getKey() + " " + x.getValue());
        });
        System.out.println(Arrays.toString(map.table));
    }

    @Nullable
    @Override
    public V put(K key, V value) {
        int h = spread(key.hashCode());
        for (PointerNode<K,V> x;;) {
            PointerNode<K,V>[] tab = table;
            int n = h & (tab.length - 1);
            if ((x = tabAt(tab, n)) == null) {

                Node<K,V> newNode = new Node<>(h, key, value);

                PointerNode<K,V> start = new PointerNode<>(newNode);

                if ((x = caeTabAt(tab, n, null, start)) == null) {
                    offer(start);
                    size.getAndIncrement();
                    return null;
                }
            }
            x.lock();
            try {
                if (table == tab) {

                    Node<K,V> e = x.find(h, key);

                    if (e != null) {
                        e.val = value;
                    } else {
                        x.linkLast(h, key, value);
                    }
                    return null; // todo:
                }
            } finally {
                x.unlock();
            }
        }
    }

    @Override
    public V remove(Object key) {
        int h = spread(key.hashCode());
        for (PointerNode<K,V> x;;) {
            PointerNode<K,V>[] tab = table;
            int n = h & (tab.length - 1);

            if ((x = tabAt(tab, n)) == null)
                return null;
            else {
                x.lock();
                try {

                    if (x.unlink(key) &&
                            casTabAt(tab, n, x, null)) {

                        PointerNode<K,V> p = x.prev, q = x.next;

                        for (PointerNode<K,V> a = q; a != null;) {
                            PointerNode<K,V> f = a.prev;
                            a = tryFindPrevActiveNode(a);
                            if (PREV.weakCompareAndSet(q, f, a)) {
                                break;
                            }
                        }
                        for (PointerNode<K,V> a = p; a != null;) {
                            PointerNode<K,V> f = a.next;
                            a = tryFindNextActiveNode(a);
                            if (NEXT.weakCompareAndSet(p, f, a)) {
                                break;
                            }
                        }
                    }
                } finally {
                    x.lock();
                }
            }
        }
    }

    private void offer(PointerNode<K,V> newNode) {
        for (;;) {
            PointerNode<K,V> l = last;

            for (;;) {
                PointerNode<K,V> x = l.next;
                if (x != null)
                    l = x;
                else
                    break;
            }
            PREV.set(newNode, l);

            if (NEXT.weakCompareAndSet(l, null, newNode)) {
                TAIL.compareAndSet(this, l, newNode);
                return;
            }
        }
    }


    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {

    }

    @Override
    public void clear() {

    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return null;
    }

    @Override
    public V putIfAbsent(@NotNull K key, V value) {
        return null;
    }

    @Override
    public boolean remove(@NotNull Object key, Object value) {
        return false;
    }

    @Override
    public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
        return false;
    }

    @Override
    public V replace(@NotNull K key, @NotNull V value) {
        return null;
    }

    static class PointerNode<K,V> extends ReentrantLock {

        // todo: red-black tree
        volatile Node<K,V> tail;

        volatile boolean dead;
        volatile PointerNode<K,V> prev, next;

        PointerNode(Node<K,V> start) {
            tail = start;
        }


        void linkLast(int h, K key, V val) {
            // assert isLocked();
            tail = new Node<>(h, key, val, tail);
        }
        // x y (z) t

        boolean unlink(Object key) {
            // assert isLocked();
            for (Node<K,V> x = tail; x != null;) {

                Node<K,V> n = x.prev;

                if (Objects.equals(n.key, key)) {

                    x.prev = n.prev;
                    return false;
                }
                x = n;
            }
            return dead = true;
        }

        Node<K,V> find(int h, Object k) {
            for (Node<K,V> x = tail; x != null; x = x.prev) {
                K ek;
                if (x.hash == h &&
                        ((ek = x.key) == k || (k.equals(ek))))
                    return x;
            }
            return null;
        }
        public void forEach(Consumer<Map.Entry<K,V>> action) {
            if (!dead)
                forEach0(tail, action);
        }

        private void forEach0(Node<K,V> x,
                              Consumer<Map.Entry<K,V>> action) {
            if (x != null) {
                forEach0(x.prev, action);
                action.accept(Map.entry(x.key, x.val));
            }
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ");

            forEach(x -> joiner.add(x.getKey() + "=" + x.getValue()));
            return joiner.toString();
        }
    }
    PointerNode<K,V> tryFindNextActiveNode(PointerNode<K,V> src) {
        PointerNode<K,V> n = src;
        do { n = n.next; }
        while (n != null && n.dead);
        return n;
    }

    PointerNode<K,V> tryFindPrevActiveNode(PointerNode<K,V> src) {
        PointerNode<K,V> p = src;
        do { p = p.prev; }
        while (p != null && p.dead);
        return p;
    }

    public void forEach(Consumer<Map.Entry<K,V>> action) {
        forEach0(last, action);
    }

    private void forEach0(PointerNode<K,V> x,
                          Consumer<Map.Entry<K,V>> action) {
        if (x != null) {
            forEach0(x.prev, action);
            x.forEach(action);
        }
    }


    static class Node<K,V> extends ReentrantLock
            implements Map.Entry<K,V> {
        final int hash;
        final K key;
        volatile V val;
        volatile Node<K,V> prev;

        Node(int hash, K key, V value) {
            this.hash = hash;
            this.key = key;
            this.val = value;
        }
        Node(int hash, K key, V value, Node<K,V> prev) {
            this(hash, key, value);
            this.prev = prev;
        }

        @Override
        public final K getKey()     { return key; }
        @Override
        public final V getValue()   { return val; }
        @Override
        public final int hashCode() { return hash ^ val.hashCode(); }
        @Override
        public final String toString() {
            return key + "=" + val;
        }
        @Override
        public final V setValue(V value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public final boolean equals(Object o) {
            Object k, v, u; Map.Entry<?,?> e;
            return ((o instanceof Map.Entry) &&
                    (k = (e = (Map.Entry<?,?>)o).getKey()) != null &&
                    (v = e.getValue()) != null &&
                    (k == key || k.equals(key)) &&
                    (v == (u = val) || v.equals(u)));
        }
    }
    /*
     * Atomic access methods are used for array
     */
    private static <K,V> PointerNode<K,V>
    tabAt(PointerNode<K,V>[] tab, int i) {
        return (PointerNode<K, V>) AA.getAcquire(tab, i);
    }
    private static <K,V> PointerNode<K,V>
    caeTabAt(PointerNode<K,V>[] tab, int i,
             PointerNode<K,V> c,
             PointerNode<K,V> v) {
        return (PointerNode<K, V>) AA.compareAndExchange(tab, i, c, v);
    }
    private static <K,V> boolean
    casTabAt(PointerNode<K,V>[] tab, int i,
             PointerNode<K,V> c,
             PointerNode<K,V> v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    private static <K,V> PointerNode<K,V>
    getAndSetAt(PointerNode<K,V>[] tab, int i, PointerNode<K,V> v) {
        return (PointerNode<K, V>) AA.getAndSet(tab, i, v);
    }

    private record Index<K,V>(
            PointerNode<K,V> prev,
            PointerNode<K,V> next
    ) { }

    // VarHandle mechanics
    private static final VarHandle TAIL;

    private static final VarHandle PREV, NEXT;

    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(PointerNode[].class);

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            TAIL = l.findVarHandle(ConcurrentLinkedHashMap.class, "last",
                    PointerNode.class);

            NEXT = l.findVarHandle(PointerNode.class, "next",
                    PointerNode.class);
            PREV = l.findVarHandle(PointerNode.class, "prev",
                    PointerNode.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
