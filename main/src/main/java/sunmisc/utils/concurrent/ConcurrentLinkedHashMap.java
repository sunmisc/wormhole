package sunmisc.utils.concurrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class ConcurrentLinkedHashMap<K,V> extends AbstractMap<K,V>
        implements ConcurrentMap<K,V> {

    static final int MAXIMUM_CAPACITY = 1 << 30;
    private final Node<K,V> head;
    private volatile Node<K,V> tail;
    private final AtomicInteger size = new AtomicInteger();

    // todo: UnblockingArrayBuffer
    private volatile Bin<K,V>[] table = new Bin[8];

    public ConcurrentLinkedHashMap() {
        head = tail = new Node<>() {
            @Override public K getKey() { return null; }

            @Override public V getValue() { return null; }

            @Override public boolean isDead() { return true; }
        };
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
    public V get(Object key) {
        int h = spread(key.hashCode());
        Bin<K,V>[] tab = table;
        int i = h & (tab.length - 1);
        Bin<K,V> n = tabAt(tab, i);
        if (n == null)
            return null;
        Node<K,V> x = n.find((K)key);
        return x == null ? null : x.getValue();
    }

    public K getFirstKey() {
        Node<K,V> x = tryFindNextActiveNode(head);
        return x.getKey();
    }
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ",
                "[", "]");
        for (Node<K,V> h = head; h != null; h = h.next) {
            joiner.add(String.valueOf(h.getKey()));
        }
        return joiner.toString();
    }

    public static void main(String[] args) {
        ConcurrentLinkedHashMap<Integer,Integer> map
                = new ConcurrentLinkedHashMap<>();
        map.put(34, 1);
        map.put(23, 1);
        map.put(1, 1);
        map.put(66, 1);

        map.remove(34);
        System.out.println(map.getFirstKey());
        System.out.println(map);
    }

    @Nullable
    @Override
    public V put(K key, V value) {
        int h = spread(key.hashCode());
        for (Bin<K,V> x;;) {
            Bin<K,V>[] tab = table;
            int n = h & (tab.length - 1);
            if ((x = tabAt(tab, n)) == null) {

                Node<K,V> newNode = new HashNode<>(h, key, value);

                Bin<K,V> bin = newBin(key);
                bin.addIfAbsent(newNode);

                if ((x = caeTabAt(tab, n, null, bin)) == null) {
                    offer(newNode);
                    size.getAndIncrement();
                    return null;
                }
            }
            x.lock();
            try {
                if (table == tab) {

                    HashNode<K,V> e = new HashNode<>(h, key, value);
                    Node<K,V> q = x.addIfAbsent(e);

                    if (q != null) {
                        return q.setValue(value);
                    } else {
                        offer(e);

                        return value;
                    }
                }
            } finally {
                x.unlock();
            }
        }
    }

    @Override
    public V remove(Object key) {
        int h = spread(key.hashCode());
        for (Bin<K,V> f;;) {
            Bin<K,V>[] tab = table;
            int n = h & (tab.length - 1);

            if ((f = tabAt(tab, n)) == null)
                return null;
            else {
                f.lock();
                try {

                    Node<K,V> x = f.remove((K) key);

                    if (x != null &&
                            casTabAt(tab, n, f, null)) {

                        x.setValue(null); // mark

                        Node<K,V> prev = x.prev, next = x.next;

                        if (prev != null) {
                            Node<K,V> activePrev
                                    = tryFindPrevActiveNode(prev);
                            skipDeletedSuccessors(activePrev);

                            if (next == null)
                                TAIL.compareAndSet(this, x, activePrev);
                        }
                        if (next != null) {
                            Node<K,V> activeNext
                                    = tryFindNextActiveNode(next);

                            skipDeletedPredecessors(activeNext);
                        }
                    }
                } finally {
                    f.lock();
                }
            }
        }
    }

    private void offer(Node<K,V> newNode) {
        for (;;) {
            Node<K,V> l = tail;

            for (;;) {
                Node<K,V> x = l.next;
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

    private void skipDeletedPredecessors(Node<K,V> x) {
        Node<K,V> p = x.prev,
                n = tryFindPrevActiveNode(p);
        if (p != n)
            PREV.setRelease(x, n);
    }

    private void skipDeletedSuccessors(Node<K,V> x) {
        Node<K, V> p = x.next,
                n = tryFindNextActiveNode(p);
        if (p != n)
            NEXT.setRelease(x, n);
    }
    Node<K,V> tryFindNextActiveNode(Node<K,V> src) {
        Node<K,V> n = src, p;
        while (n.isDead() && (p = n.next) != null)
            n = p;
        return n;
    }

    Node<K,V> tryFindPrevActiveNode(Node<K,V> src) {
        Node<K,V> n = src, p;
        while (n.isDead() && (p = n.prev) != null)
            n = p;
        return n;
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

    static class HashNode<K,V> extends Node<K,V> {
        final int hash;
        final K key;
        volatile V value;

        HashNode(int hash, K key, V value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        @Override public K getKey() { return key; }

        @Override public V getValue() { return value; }

        @Override
        public V setValue(V val) {
            V v = value;
            value = val;
            return v;
        }
    }

    static abstract class Node<K,V> implements Map.Entry<K,V> {
        volatile Node<K,V> prev, next;

        boolean isDead() {
            return getValue() == null;
        }

        @Override
        public final String toString() {
            return getKey() + "=" + getValue();
        }
        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }
        @Override
        public final boolean equals(Object o) {
            Object k, v, u; Map.Entry<?,?> e;

            K key = getKey();
            V val = getValue();
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
    private static <K,V> Bin<K,V>
    tabAt(Bin<K,V>[] tab, int i) {
        return (Bin<K, V>) AA.getAcquire(tab, i);
    }
    private static <K,V> Bin<K,V>
    caeTabAt(Bin<K,V>[] tab, int i,
             Bin<K,V> c,
             Bin<K,V> v) {
        return (Bin<K, V>) AA.compareAndExchange(tab, i, c, v);
    }
    private static <K,V> boolean
    casTabAt(Bin<K,V>[] tab, int i,
             Bin<K,V> c,
             Bin<K,V> v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    private static <K,V> Bin<K,V>
    getAndSetAt(Bin<K,V>[] tab, int i, Bin<K,V> v) {
        return (Bin<K, V>) AA.getAndSet(tab, i, v);
    }

    // VarHandle mechanics
    private static final VarHandle TAIL;

    private static final VarHandle PREV, NEXT;

    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Bin[].class);

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            TAIL = l.findVarHandle(ConcurrentLinkedHashMap.class, "tail",
                    Node.class);

            NEXT = l.findVarHandle(Node.class, "next",
                    Node.class);
            PREV = l.findVarHandle(Node.class, "prev",
                    Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static <K,V> AbstractBin<K,V> newBin(K key) {
        return key instanceof Comparable<?>
                ? new TreeBin<>()
                : new LinkedBin<>();
    }
    private static class TreeBin<K,V> extends AbstractBin<K,V> {

        private final TreeMap<K, Node<K,V>> tree
                = new TreeMap<>();

        @Override
        public Node<K,V> addIfAbsent(Node<K, V> n) {
            return tree.putIfAbsent(n.getKey(), n);
        }

        @Override
        public Node<K, V> remove(K key) {
            return tree.remove(key);
        }

        @Override
        public Node<K, V> find(K key) {
            return tree.get(key);
        }

        @Override
        public int size() {
            return tree.size();
        }

        @NotNull
        @Override
        public Iterator<Node<K, V>> iterator() {
            return tree.values().iterator();
        }
        @Override
        public String toString() {
            return tree.values().toString();
        }
    }

    private static class LinkedBin<K,V> extends AbstractBin<K,V> {
        private final LinkedList<Node<K,V>> list
                = new LinkedList<>(); // todo:

        @Override
        public Node<K,V> addIfAbsent(Node<K, V> n) {
            Node<K,V> f = find(n.getKey());

            if (f != null)
                return f;
            list.add(n);
            return null;
        }

        @Override
        public Node<K,V> remove(K key) {
            Iterator<Node<K,V>> itr = iterator();
            while (itr.hasNext()) {
                Node<K,V> x = itr.next();
                if (Objects.equals(x.getKey(), key)) {
                    itr.remove();
                    return x;
                }
            }
            return null;
        }

        @Override
        public Node<K, V> find(K key) {

            for (Node<K,V> n : list) {
                if (Objects.equals(n.getKey(), key)) {
                    return n;
                }
            }
            return null;
        }

        @Override
        public int size() {
            return list.size();
        }

        @NotNull
        @Override
        public Iterator<Node<K, V>> iterator() {
            return list.iterator();
        }

        @Override
        public String toString() {
            return list.toString();
        }
    }


    private abstract static class AbstractBin<K,V>
            extends ReentrantLock implements Bin<K,V> {

    }
    private interface Bin<K,V>
            extends Iterable<Node<K,V>>, Lock {
        Node<K,V> addIfAbsent(Node<K,V> n);

        Node<K,V> remove(K key);

        Node<K,V> find(K key);

        int size();
    }
}
