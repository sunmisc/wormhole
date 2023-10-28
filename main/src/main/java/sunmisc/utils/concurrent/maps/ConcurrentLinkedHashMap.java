package sunmisc.utils.concurrent.maps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sunmisc.utils.concurrent.ConcurrentSegmentBuffers;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("unchecked")
public class ConcurrentLinkedHashMap<K,V> extends AbstractMap<K,V>
        implements ConcurrentMap<K,V> {
    private final Head<K,V> head;
    private final Tail<K,V> tail;
    private volatile int size;

    private final ConcurrentSegmentBuffers<Bucket<K,V>> table
            = new ConcurrentSegmentBuffers<>();

    public ConcurrentLinkedHashMap() {
        for (int i = 0; i < 4; ++i) // default capacity = 32
            table.expand();
        Node<K,V> share = new DummyNode<>();

        Head<K,V> head = new Head<>();
        Tail<K,V> tail = new Tail<>();

        tail.prev = share;
        head.next = share;

        this.head = head;
        this.tail = tail;
    }

    static int spread(int h) {
        return (h ^ (h >>> 16));
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public V get(Object key) {
        final int h = spread(key.hashCode());
        var tab = table;
        int i = h & (tab.length() - 1);

        Bucket<K,V> n = tab.get(i);

        if (n == null)
            return null;

        Node<K,V> f = n.find((K)key, h);

        return f == null ? null : f.getValue();
    }

    private Node<K,V> firstNode() {
        return head.head();
    }

    public K getFirstKey() {
        return firstNode().getKey();
    }
    @Override
    public String toString() {

        StringJoiner joiner = new StringJoiner(", ",
                "[", "]");

        for (Node<K,V> h = firstNode(); h != null; h = h.next) {
            V val = h.getValue();
            if (!h.isDead() && val != null)
                joiner.add(val.toString());
        }
        return joiner.toString();
    }


    private V putVal(K key, V value, boolean ifAbsent) {
        final int h = spread(key.hashCode());
        var tab = table;
        int n = tab.length(), i = h & (n - 1);
        HashNode<K,V> newNode = new HashNode<>(h, key);

        outer: {
            Bucket<K,V> x = tab.get(i);
            if (x == null) {
                x = new SkipListBucket<>();
                x.addIfAbsent(newNode);
                if ((x = tab.compareAndExchange(i, null, x)) == null) {
                    break outer;
                }
            }
            synchronized (x) {
                Node<K,V> q = x.addIfAbsent(newNode);

                if (q != null) {
                    return ifAbsent ? q.getValue() : q.setValue(value);
                }
            }
        }
        tail.linkLast(newNode);
        // commit
        V val = newNode.setValue(value);

        if ((int) SIZE.getAndAdd(this, 1) + 1 >= n)
            tab.expand();
        return val;
    }
    @Nullable
    @Override
    public V put(K key, V value) {
        return putVal(key, value, false);
    }
    @Override
    public boolean replace(@NotNull K key,
                           @NotNull V oldValue,
                           @NotNull V newValue) {
        final int h = spread(key.hashCode());
        var tab = table;
        int n = h & tab.length();
        Bucket<K,V> x = tab.get(n);

        if (x == null)
            return false;
        synchronized (x) {

            Node<K, V> e = x.find(key, h);

            if (e != null && Objects.equals(e.getValue(), oldValue)) {
                e.setValue(newValue);
                return true;
            }
            return false;
        }
    }
    private V removeVal(K key, V expected) {
        final int h = spread(key.hashCode());
        var tab = table;

        int n = h & (tab.length() - 1);

        Bucket<K,V> f = tab.get(n);

        if (f == null) return null;

        synchronized (f) {
            Node<K,V> e = expected == null
                    ? f.remove(key, h)
                    : f.remove(key, h, expected);

            if (e == null)
                return null;

            expected = e.setValue(null); // mark

            if (f.empty())
                tab.set(n, null);
            unlink(e);
        }
        SIZE.getAndAddRelease(this, -1);
        return expected;
    }

    @Override
    public V remove(Object key) {
        return removeVal((K) key, null);
    }

    private void unlink(Node<K, V> x) {
        Node<K,V> prev = x.prev, next = x.next;
        if (prev != null) {
            Node<K,V> activePrev = prev.tryFindPrevActiveNode();
            updateNext(activePrev);
        }
        if (next != null) {
            Node<K,V> activeNext
                    = next.tryFindNextActiveNode();
            updatePrev(activeNext);
        }
        updateNext(head.next);
        updatePrev(tail.prev);
    }
    private void updatePrev(Node<K,V> x) {
        Node<K,V> p, n;
        do {
            if ((p = x.prev) == null) // todo: head.prev = ?
                break;
            n = p.tryFindPrevActiveNode();
        } while (p != n && !x.casPrev(p, n));
    }

    private void updateNext(Node<K,V> x) {
        Node<K,V> p, n;
        do {
            if ((p = x.next) == null) // todo: tail.next = ?
                break;
            n = p.tryFindNextActiveNode();
        } while (p != n && !x.casNext(p, n));

    }

    @NotNull
    @Override
    public Set<Entry<K,V>> entrySet() {
        return null;
    }

    @Override
    public V putIfAbsent(@NotNull K key, V value) {
        return putVal(key, value, true);
    }

    @Override
    public boolean remove(@NotNull Object key, Object value) {
        return removeVal((K) key, (V) value) == value;
    }


    @Override
    public V replace(@NotNull K key, @NotNull V value) {
        return put(key, value);
    }

    static class HashNode<K,V> extends Node<K,V> {
        final int hash;
        final K key;
        volatile V value;

        HashNode(int hash, K key) {
            this.hash = hash;
            this.key = key;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }
        @Override
        public V setValue(V val) {
            V v = value;
            value = val;
            return v;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            HashNode<?, ?> hashNode = (HashNode<?, ?>) o;
            return hash == hashNode.hash
                    && Objects.equals(key, hashNode.key)
                    && Objects.equals(value, hashNode.value);
        }

        @Override
        public int hashCode() {
            return hash ^ Objects.hashCode(value);
        }
    }


    private static final class Head<K,V> extends DummyNode<K,V> {

        Node<K,V> head() {
            for (; ; ) {
                final Node<K,V>  t = next;
                Node<K,V> pred = t.prev;
                if (pred == null)
                    return t;
                Node<K,V>  k = pred.tryFindPrevActiveNode();

                if (t == k || casNext(t, k))
                    return k;
            }
        }

        @Override
        Node<K, V> tryFindNextActiveNode() {
            return next.tryFindNextActiveNode();
        }

        @Override
        Node<K, V> tryFindPrevActiveNode() {
            throw new IllegalStateException();
        }

        @Override public String toString() { return "head"; }
    }
    private static final class Tail<K,V> extends DummyNode<K,V> {

        Node<K,V> tail() {
            for (; ; ) {
                final Node<K,V>  t = prev;
                Node<K,V> succ = t.next;
                if (succ == null)
                    return t;
                Node<K,V>  k = succ.tryFindNextActiveNode();

                if (t == k || casPrev(t, k))
                    return k;
            }
        }
        void linkLast(Node<K,V> newNode) {
            for (; ; ) {
                Node<K,V> t = tail(); // <- help and get

                // relaxed
                PREV.set(newNode, t);

                // fence
                if (t.casNext(null, newNode)) {
                    casPrev(t, newNode);
                    break;
                }
            }
        }
        @Override
        Node<K, V> tryFindNextActiveNode() {
            throw new IllegalStateException();
        }

        @Override
        Node<K, V> tryFindPrevActiveNode() {
            return prev.tryFindNextActiveNode();
        }
        @Override public String toString() { return "tail"; }
    }
    static abstract class Node<K,V> implements Map.Entry<K,V> {
        volatile Node<K,V> prev, next;

        boolean isDead() {
            return getValue() == null;
        }
        @Override
        public String toString() {
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

        final boolean casNext(Node<K,V> expected, Node<K,V> newNode) {
            return NEXT.compareAndSet(this, expected, newNode);
        }
        final boolean casPrev(Node<K,V> expected, Node<K,V> newNode) {
            return PREV.compareAndSet(this, expected, newNode);
        }

        Node<K, V> tryFindNextActiveNode() {
            Node<K, V> n = this, p;
            for (; n.isDead() && (p = n.next) != null; n = p);
            return n;
        }
        Node<K, V> tryFindPrevActiveNode() {
            Node<K, V> n = this, p;
            for (; n.isDead() && (p = n.prev) != null; n = p);
            return n;
        }

        @Override
        public boolean equals(Object o) {
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

    // VarHandle mechanics
    private static final VarHandle PREV, NEXT;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();

            NEXT = l.findVarHandle(Node.class, "next",
                    Node.class);
            PREV = l.findVarHandle(Node.class, "prev",
                    Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static class SkipListBucket<K,V> implements Bucket<K,V> {

        private final SkipListBin<K,V> bin = new SkipListBin<>();
        @Override
        public Node<K, V> addIfAbsent(HashNode<K, V> n) {
            return bin.putIfAbsent(n);
        }

        @Override
        public Node<K, V> remove(K key, int h) {
            return bin.doRemove(key, h, null);
        }

        @Override
        public Node<K, V> remove(K key, int h, V expected) {
            return bin.doRemove(key, h, expected);
        }

        @Override
        public Node<K, V> find(K key, int h) {
            return bin.get(key, h);
        }

        @Override
        public boolean empty() {
            return bin.empty();
        }
    }

    private static class DummyNode<K,V> extends Node<K,V> {

        @Override public K getKey() { return null; }

        @Override public V getValue() { return null; }

        @Override
        boolean isDead() { return true; }
    }

    private interface Bucket<K,V> {
        Node<K,V> addIfAbsent(HashNode<K,V> n);

        Node<K,V> remove(K key, int h);

        Node<K,V> remove(K key, int h, V expected);

        Node<K,V> find(K key, int h);

        boolean empty();
    }

    private static final VarHandle SIZE;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();

            SIZE = l.findVarHandle(ConcurrentLinkedHashMap.class,
                    "size", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
