package sunmisc.utils.concurrent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("unchecked")
class ConcurrentLinkedHashMap<K,V> extends AbstractMap<K,V>
        implements ConcurrentMap<K,V> {
    static final int MAXIMUM_CAPACITY = 1 << 30;
    private final Head<K,V> head;
    private final Tail<K,V> tail;
    private volatile int size;

    // todo: UnblockingArrayBuffer
    private volatile Bucket<K,V>[] table = new Bucket[1 << 7];

    public ConcurrentLinkedHashMap() {
        Node<K,V> share = new DummyNode<>();

        Head<K,V> head = new Head<>();
        head.next = share;
        Tail<K,V> tail = new Tail<>();
        tail.prev = share;

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
        Bucket<K,V>[] tab = table;
        int i = h & (tab.length - 1);
        Bucket<K,V> n = tabAt(tab, i);
        if (n == null)
            return null;
        return n.find((K)key)
                .map(Entry::getValue)
                .orElse(null);
    }

    private Node<K,V> firstNode() {
        return head.tryFindNextActiveNode();
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
            if (val != null)
                joiner.add(val.toString());
        }
        return joiner.toString();
    }


    private V putVal(K key, V value, boolean ifAbsent) {
        final int h = spread(key.hashCode());
        Bucket<K,V>[] tab = table;
        int n = h & (tab.length - 1);

        Node<K,V> newNode = new HashNode<>(h, key);

        outer: {
            Bucket<K,V> x = tabAt(tab, n);
            if (x == null) {
                x = newBin(key);
                x.addIfAbsent(newNode);
                if ((x = caeTabAt(tab, n, null, x)) == null) {
                    break outer;
                }
            }
            x.lock();
            try {
                Optional<Node<K,V>> old = x.addIfAbsent(newNode);

                if (old.isPresent()) {
                    Node<K,V> q = old.get();
                    return ifAbsent ? q.getValue() : q.setValue(value);
                }
            } finally {
                x.unlock();
            }
        }
        tail.linkLast(newNode);
        // committed;
        V val = newNode.setValue(value);
        SIZE.getAndAdd(this, 1);
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
        Bucket<K,V>[] tab = table;
        int n = h & (tab.length - 1);
        Bucket<K,V> x = tabAt(tab, n);

        if (x == null) {
            return false;
        } else {
            x.lock();
            try {
                return x.find(key)
                        .map(e -> {
                            if (Objects.equals(e.getValue(), oldValue)) {
                                e.setValue(newValue);
                                return true;
                            }
                            return false;
                        })
                        .orElse(false);
            } finally {
                x.unlock();
            }
        }
    }
    private V removeVal(K key, V expected) {
        final int h = spread(key.hashCode());
        Bucket<K,V>[] tab = table;

        int n = h & (tab.length - 1);

        Bucket<K,V> f = tabAt(tab, n);

        if (f == null)
            return null;
        else {
            f.lock();
            try {
                Optional<Node<K,V>> x = expected == null
                        ? f.remove(key)
                        : f.remove(key, expected);

                if (x.isEmpty())
                    return null;

                Node<K,V> e = x.get();
                expected = e.setValue(null); // mark

                if (f.empty())
                    setAt(tab, n, null);
                unlink(e);
            } finally {
                f.unlock();
            }
            SIZE.getAndAdd(this, -1);
            return expected;
        }
    }

    @Override
    public V remove(Object key) {
        return removeVal((K) key, null);
    }

    private void unlink(Node<K,V> x) {
        Node<K,V> prev = x.prev, next = x.next;
        if (prev != null) {
            Node<K,V> activePrev
                    = prev.tryFindPrevActiveNode();
            skipDeadNodesAndSetNext(activePrev);
        }
        if (next != null) {
            Node<K,V> activeNext
                    = next.tryFindNextActiveNode();
            skipDeadNodesAndSetPrev(activeNext);
        }
    }
    private void skipDeadNodesAndSetNext(Node<K,V> x) {
        for (;;) {
            Node<K,V> p = x.next;
            if (p == null)
                break;
            Node<K,V> n = p.tryFindNextActiveNode();
            if (p != n) {
                if (x.casNext(p, n)) {
                    break;
                }
            } else break;
        }
    }

    private void skipDeadNodesAndSetPrev(Node<K,V> x) {
        for (;;) {
            Node<K,V> p = x.prev;
            if (p == null)
                break;
            Node<K,V> n = p.tryFindPrevActiveNode();
            if (p != n) {
                if (x.casPrev(p, n)) {
                    break;
                }
            } else break;
        }
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

        @Override public K getKey() { return key; }

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
    }

    private static final class Head<K,V> extends DummyNode<K,V> {
        void linkFirst(Node<K,V> newNode) {
            for (Node<K,V> head;;) {
                // plain
                NEXT.set(newNode, head = next);
                // memory barrier
                if (head.casPrev(null, newNode)) {
                    casNext(head, newNode);
                    break;
                }
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
        void linkLast(Node<K,V> newNode) {
            for (Node<K,V> tail;;) {
                // plain
                PREV.set(newNode, tail = prev);
                // memory barrier
                if (tail.casNext(null, newNode)) {
                    casPrev(tail, newNode);
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
        Node<K,V> tryFindNextActiveNode() {
            Node<K,V> n = this, p;
            while (n.isDead() && (p = n.next) != null)
                n = p;
            return n;
        }

        Node<K,V> tryFindPrevActiveNode() {
            Node<K,V> n = this, p;
            while (n.isDead() && (p = n.prev) != null)
                n = p;
            return n;
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
    private static <K,V> Bucket<K,V>
    tabAt(Bucket<K,V>[] tab, int i) {
        return (Bucket<K,V>) AA.getAcquire(tab, i);
    }
    private static <K,V> Bucket<K,V>
    caeTabAt(Bucket<K,V>[] tab, int i,
             Bucket<K,V> c,
             Bucket<K,V> v) {
        return (Bucket<K,V>) AA.compareAndExchange(tab, i, c, v);
    }
    private static <K,V> boolean
    casTabAt(Bucket<K,V>[] tab, int i,
             Bucket<K,V> c,
             Bucket<K,V> v) {
        return AA.compareAndSet(tab, i, c, v);
    }
    private static <K,V> Bucket<K,V>
    getAndSetAt(Bucket<K,V>[] tab, int i, Bucket<K,V> v) {
        return (Bucket<K,V>) AA.getAndSet(tab, i, v);
    }
    private static <K,V> void
    setAt(Bucket<K,V>[] tab, int i, Bucket<K,V> v) {
        AA.setRelease(tab, i, v);
    }

    // VarHandle mechanics
    private static final VarHandle PREV, NEXT;

    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Bucket[].class);

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

    private static <K,V> AbstractBucket<K,V> newBin(K key) {
        return key instanceof Comparable<?>
                ? new TreeBucket<>()
                : new LinkedBucket<>();
    }

    private static class TreeBucket<K,V> extends AbstractBucket<K,V> {

        private final ConcurrentSkipListMap<K, Node<K,V>> tree
                = new ConcurrentSkipListMap<>();


        @Override
        public Optional<Node<K,V>> addIfAbsent(Node<K,V> n) {
            return Optional.ofNullable(tree.putIfAbsent(n.getKey(), n));
        }

        @Override
        public Optional<Node<K,V>> remove(K key) {
            return Optional.ofNullable(tree.remove(key));
        }

        @Override
        public Optional<Node<K,V>> remove(K key, V expected) {
            AtomicReference<Node<K,V>> ref = new AtomicReference<>();

            tree.computeIfPresent(key,
                    (k,v) -> {

                        if (Objects.equals(v.getValue(), expected)) {
                            ref.setPlain(v);
                            return null;
                        }
                        return v;
                    });
            return Optional.ofNullable(ref.getPlain());
        }

        @Override
        public Optional<Node<K,V>> find(K key) {
            return Optional.ofNullable(tree.get(key));
        }

        @Override
        public boolean empty() {
            return tree.isEmpty();
        }

        @Override
        public String toString() {
            return tree.values().toString();
        }
    }

    private static class LinkedBucket<K,V> extends AbstractBucket<K,V> {

        private final Entry<K,V> head = new Entry<>(null);

        @Override
        public Optional<Node<K,V>> addIfAbsent(Node<K,V> n) {
            final K key = n.getKey();
            Entry<K,V> h = head;
            for (;;) {
                Node<K,V> x = h.node;
                if (x != null &&
                        Objects.equals(x.getKey(), key))
                    return Optional.of(x);
                Entry<K,V> next = h.next;
                if (next == null)
                    break;
                h = next;
            }
            h.next = new Entry<>(n);
            return Optional.empty();
        }

        @Override
        public Optional<Node<K,V>> remove(K key) {
            for (Entry<K,V> h = head; h != null;) {
                Node<K,V> x = h.node;
                Entry<K,V> next = h.next;
                if (x != null &&
                        Objects.equals(x.getKey(), key)) {
                    h.next = next.next;
                    return Optional.of(x);
                }
                h = next;
            }
            return Optional.empty();
        }

        @Override
        public Optional<Node<K,V>> remove(K key, V expected) {
            for (Entry<K,V> h = head; h != null;) {
                Node<K,V> x = h.node;
                Entry<K,V> next = h.next;
                if (x != null &&
                        Objects.equals(x.getKey(), key) &&
                        Objects.equals(x.getValue(), expected)) {
                    h.next = next.next;
                    return Optional.of(x);
                }
                h = next;
            }
            return Optional.empty();
        }

        @Override
        public Optional<Node<K,V>> find(K key) {
            for (Entry<K,V> h = head; h != null; h = h.next) {
                Node<K,V> x = h.node;
                if (Objects.equals(x.getKey(), key))
                    return Optional.of(x);
            }
            return Optional.empty();
        }

        @Override
        public boolean empty() {
            return head.next == null;
        }

        private static class Entry<K,V> {
            volatile Entry<K,V> next;

            final Node<K,V> node;

            Entry(Node<K,V> node) {
                this.node = node;
            }
        }
    }


    private abstract static class AbstractBucket<K,V>
            extends ReentrantLock
            implements Bucket<K,V> {
    }

    private static class DummyNode<K,V> extends Node<K,V> {

        @Override public K getKey() { return null; }

        @Override public V getValue() { return null; }
    }

    private interface Bucket<K,V> extends Lock {
        Optional<Node<K,V>> addIfAbsent(Node<K,V> n);

        Optional<Node<K,V>> remove(K key);

        Optional<Node<K,V>> remove(K key, V expected);

        Optional<Node<K,V>> find(K key);

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
