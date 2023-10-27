package sunmisc.utils.concurrent.maps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unchecked")
public class ConcurrentLinkedHashMap<K,V> extends AbstractMap<K,V>
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
        Bucket<K,V>[] tab = table;
        int i = h & (tab.length - 1);
        Bucket<K,V> n = tabAt(tab, i);
        return n == null ? null : n.find((K)key)
                .map(Entry::getValue)
                .orElse(null);
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
        int g = key.hashCode();
        final int h = spread(g);
        Bucket<K, V>[] tab = table;
        int n = h & (tab.length - 1);

        Node<K,V> newNode = new HashNode<>(g, key);

        outer: {
            Bucket<K,V> x = tabAt(tab, n);
            if (x == null) {
                x = new SkipListBucket<>();
                x.addIfAbsent(newNode);
                if ((x = caeTabAt(tab, n, null, x)) == null) {
                    break outer;
                }
            }
            synchronized (x) {
                Optional<Node<K,V>> old = x.addIfAbsent(newNode);

                if (old.isPresent()) {
                    Node<K,V> q = old.get();
                    return ifAbsent ? q.getValue() : q.setValue(value);
                }
            }
        }
        // committed;
        tail.linkLast(newNode);
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

        if (x == null)
            return false;
        synchronized (x) {
            return x.find(key)
                    .map(e -> {
                        if (Objects.equals(e.getValue(), oldValue)) {
                            e.setValue(newValue);
                            return true;
                        }
                        return false;
                    })
                    .orElse(false);
        }
    }
    private V removeVal(K key, V expected) {
        final int h = spread(key.hashCode());
        Bucket<K,V>[] tab = table;

        int n = h & (tab.length - 1);

        Bucket<K,V> f = tabAt(tab, n);

        if (f == null) return null;

        synchronized (f) {
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
        }
        SIZE.getAndAdd(this, -1);
        return expected;
    }

    @Override
    public V remove(Object key) {
        return removeVal((K) key, null);
    }

    private void unlink(Node<K, V> x) {
        Node<K, V> prev = x.prev, next = x.next;
        if (prev != null) {
            Node<K, V> activePrev = prev.tryFindPrevActiveNode();
            updateNext(activePrev);
        }
        if (next != null) {
            Node<K, V> activeNext
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


    private static class SkipListBucket<K,V> implements Bucket<K,V> {
        private final ConcurrentSkipListMap<Map.Entry<K,?>, Node<K, V>> skipListSet0;


        private final Map<K, Node<K, V>> skipListSet = new HashMap<>();

        SkipListBucket() {
            this.skipListSet0 = new ConcurrentSkipListMap<>((o1, o2) -> {
                K k1 = o1.getKey(), k2 = o2.getKey();

                int h1 = o1 instanceof HashNode<K,?> x ? x.hash : k1.hashCode(),
                    h2 = o2 instanceof HashNode<K,?> x ? x.hash : k2.hashCode();

                int cmp = Integer.compare(h1, h2);

                boolean insertMode =
                        o1 instanceof HashNode<K,?> &&
                                o2 instanceof HashNode<K,?>;

                if (cmp == 0 && insertMode) {
                    Class<?> kc = comparableClassFor(k1);

                    if ((cmp = compareComparables(kc, k1, k2)) == 0)
                        cmp = tieBreakOrder(k1, k2);

                }
                return cmp;
            });
        }

        static Class<?> comparableClassFor(Object x) {
            if (x instanceof Comparable) {
                Class<?> c;
                if ((c = x.getClass()) == String.class) // bypass checks
                    return c;
                Type[] ts = c.getGenericInterfaces(), as;
                for (Type t : ts) {
                    if (t instanceof ParameterizedType p &&
                            p.getRawType() == Comparable.class &&
                            (as = p.getActualTypeArguments()) != null &&
                            as.length == 1 && as[0] == c) // type arg is c
                        return c;
                }
            }
            return null;
        }

        @SuppressWarnings({"rawtypes","unchecked"}) // for cast to Comparable
        static int compareComparables(Class<?> kc, Object k, Object x) {
            return (x == null || x.getClass() != kc ? 0 :
                    ((Comparable)k).compareTo(x));
        }

        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null ||
                    (d = a.getClass().getName().
                            compareTo(b.getClass().getName())) == 0)
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
                        -1 : 1);
            return d;
        }

        @Override
        public Optional<Node<K, V>> addIfAbsent(Node<K, V> n) {
            return Optional.ofNullable(skipListSet.putIfAbsent(n.getKey(), n));
        }

        @Override
        public Optional<Node<K,V>> remove(K key) {
            var k = Map.entry(key, Boolean.TRUE);

            return Optional.ofNullable(skipListSet.remove(key));
        }

        @Override
        public Optional<Node<K,V>> remove(K key, V expected) {
            AtomicReference<Node<K,V>> ref = new AtomicReference<>();

            var k = Map.entry(key, Boolean.TRUE);

            skipListSet.computeIfPresent(key,
                    (h, v) -> {

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
            var k = Map.entry(key, Boolean.TRUE);

            return Optional.ofNullable(skipListSet.get(k));
        }

        @Override
        public boolean empty() {
            return skipListSet.isEmpty();
        }
    }

    private static class DummyNode<K,V> extends Node<K,V> {

        @Override public K getKey() { return null; }

        @Override public V getValue() { return null; }
    }

    private interface Bucket<K,V> {
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
