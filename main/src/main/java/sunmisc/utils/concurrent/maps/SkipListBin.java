package sunmisc.utils.concurrent.maps;

import sunmisc.utils.concurrent.maps.ConcurrentLinkedHashMap.HashNode;

import java.lang.invoke.VarHandle;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

// for ConcurrentLinkedList

@SuppressWarnings("forRemoval")
class SkipListBin<K,V> {
    private static final int PROBABILITY = (int) (Integer.MIN_VALUE +
            0.25F // prop 1/4
            * (1L << 32));

    private Index<K,V> head = new Index<>(
            new QNode<>(null, null),
            null, null);

    private static <K,V>
    int findToComparisons(K k, int h, HashNode<K,V> p) {
        int ph, dir; K pk;
        Class<?> kc;
        if ((ph = p.hash) > h)
            return -1;
        else if (ph < h)
            return 1;
        else if (Objects.equals(pk = p.key, k))
            return 0;
        else if ((kc = comparableClassFor(k)) != null &&
                (dir = compareComparables(kc, k, pk)) != 0)
            return (dir < 0) ? -1 : 1;
        else
            return 0;
    }

    private static <K,V>
    int insertionToComparisons(HashNode<K,V> newNode, HashNode<K,V> p) {
        K k = newNode.key, pk = p.key;
        int ph, dir, h = newNode.hash;
        Class<?> kc;
        if ((ph = p.hash) > h)
            dir = -1;
        else if (ph < h)
            dir = 1;
        else if (Objects.equals(k, pk))
            return 0;
        else if ((kc = comparableClassFor(k)) == null ||
                (dir = compareComparables(kc, k, pk)) == 0)
            dir = tieBreakOrder(k, pk);
        return dir <= 0 ? -1 : 1;
    }

    private static class Index<K,V> {
        final QNode<K,V> node;
        final Index<K,V> down;
        Index<K,V> right;

        Index(QNode<K,V> node, Index<K,V> down, Index<K,V> right) {
            this.node = node;
            this.down = down;
            this.right = right;
        }
    }
    private static class QNode<K,V> {
        HashNode<K,V> key;
        QNode<K, V> next;

        QNode(HashNode<K,V> key, QNode<K, V> next) {
            this.key = key;
            this.next = next;
        }
        QNode<K,V> find(K key, int h) {
            for (QNode<K,V> n = next; n != null; n = n.next) {
                HashNode<K,V> k = n.key;
                if (k != null) {
                    int c = findToComparisons(key, h, k);
                    if (c < 0)
                        return null;
                    else if (c == 0)
                        return n;
                }
            }
            return null;
        }
    }
    HashNode<K,V> get(K key, int h) {
        VarHandle.fullFence();

        QNode<K,V> x = findPredecessor(key, h);

        return x == null ? null :
                (x = x.find(key, h)) == null ?
                        null : x.key;
    }
    HashNode<K,V> putIfAbsent(HashNode<K,V> key) {
        int levels = 0;
        Index<K,V> q = head;
        for (Index<K,V> r, d; ;) {
            while ((r = q.right) != null) {
                QNode<K,V> p = r.node;
                if (p == null || p.key == null)
                    q.right = r.right; // clean index
                else if (insertionToComparisons(key, p.key) > 0)
                    q = r;
                else
                    break;
            }
            if ((d = q.down) != null) {
                ++levels;
                q = d;
            } else break;
        }
        QNode<K,V> b = q.node, n = b.next;
        while (n != null) {
            QNode<K,V> f = n.next;
            HashNode<K,V> v = n.key;
            int c = insertionToComparisons(key, v);
            if (c > 0) {
                b = n;
                n = f;
            } else if (c == 0)
                return v;
            else
                break;
        }

        QNode<K,V> z = new QNode<>(key, n);
        b.next = z;
        tryAddLevel(levels, z);
        return null;
    }
    HashNode<K,V> doRemove(K key, int h, V value) {

        QNode<K,V> b = findPredecessor(key, h);

        if (b != null) {
            QNode<K,V> n = b.find(key, h);
            if (n != null) {
                HashNode<K,V> v = n.key;
                if (value == null || Objects.equals(v.value, value)) {

                    n.key = null; // mark

                    QNode<K,V> f = n.next;
                    n.next = new QNode<>(null, f);
                    b.next = f;

                    reduceLevel();

                    return v;
                }
            }
        }
        return null;
    }
    private void tryAddLevel(int levels, QNode<K,V> z) {
        Random random = ThreadLocalRandom.current();

        if (random.nextInt() > PROBABILITY) {
            long rnd = random.nextLong();
            int skips = levels;

            Index<K,V> x = null;
            for (;;) {
                x = new Index<>(z, x, null);
                if (rnd >= 0L || --skips < 0)
                    break;
                else
                    rnd <<= 1; //  max 62
            }
            Index<K,V> h = head;

            if (addIndices(h, skips, x) && skips < 0) {
                Index<K,V> hx = new Index<>(z, x, null);
                head = new Index<>(h.node, h, hx);
            }
        }
    }
    private void reduceLevel() {
        Index<K,V> h = head, d, e;
        if (h.right == null &&
                (d = h.down) != null && d.right == null &&
                (e = d.down) != null && e.right == null)
            head = d;
    }

    boolean empty() {
        QNode<K,V> b = head.node;
        return b.next == null;
    }

    private QNode<K,V> findPredecessor(K key, int h) {
        for (Index<K,V> q = head, r, d; ;) {
            while ((r = q.right) != null) {
                QNode<K,V> p = r.node;
                if (p == null || p.key == null)
                    q.right = r.right; // clean index
                else if (findToComparisons(key, h, p.key) > 0)
                    q = r;
                else
                    break;
            }
            if ((d = q.down) != null)
                q = d;
            else
                return q.node;
        }
    }
    private boolean addIndices(Index<K,V> q, int skips, Index<K,V> x) {
        for (HashNode<K,V> key = x.node.key; ; ) { // find splice point
            Index<K,V> r, d;
            int c;
            if ((r = q.right) != null) {
                QNode<K,V> p = r.node;
                if (p == null || p.key == null) {
                    q.right = r.right; // clean index
                    continue;
                } else if ((c = insertionToComparisons(key, p.key)) > 0)
                    q = r;
                else if (c == 0)
                    break;                      // stale
            } else
                c = -1;

            if (c < 0) {
                if ((d = q.down) != null && skips > 0) {
                    --skips;
                    q = d;
                } else if (d != null &&
                        !addIndices(d, 0, x.down)) {
                    break;
                } else {
                    x.right = r;
                    q.right = x;
                    return true;
                }
            }
        }
        return false;
    }
    private static int tieBreakOrder(Object a, Object b) {
        int d;
        if (a == null || b == null ||
                (d = a.getClass().getName().
                        compareTo(b.getClass().getName())) == 0)
            d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
                    -1 : 1);
        return d;
    }
    private static Class<?> comparableClassFor(Object x) {
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

    /**
     * Returns k.compareTo(x) if x matches kc (k's screened comparable
     * class), else 0.
     */
    @SuppressWarnings({"rawtypes","unchecked"}) // for cast to Comparable
    private static int compareComparables(Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc ? 0 :
                ((Comparable) k).compareTo(x));
    }

}
