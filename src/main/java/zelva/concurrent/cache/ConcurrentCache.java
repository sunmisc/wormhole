package zelva.concurrent.cache;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The main purpose of this cache is completely
 * asynchronous loading / unloading
 * guarantees fully consistent cache behavior
 * cache uses thread safe hash table
 * value - the node containing to write time and the last read
 * removing one of the "status" fields in
 * ConcurrentCache is based on a non-blocking future
 * we cannot know when the deletion process will occur
 * cache safely sets up exactly one delete process for each sync block
 * to avoid double assignments that we might lose
 * (otherwise it will be deleted two or more times)
 * deletion can only start after loading the value
 *
 * @author ZelvaLea
 */
@Deprecated
public class ConcurrentCache<K,V> {
    /**
     * master planner for deleting expired Nodes
     */
    static final ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, "Cache-Cleaner");
        thread.setDaemon(true);
        return thread;
    });

    // builder
    private final long afterAccess;

    /* ---------------- Fields -------------- */
    private final ConcurrentMap<K, Node<V>> map;

    public ConcurrentCache(CacheBuilder<K,V> builder) {
        this.map = new ConcurrentHashMap<>(builder.maxSize, builder.loadFactor);
        this.afterAccess = builder.expireAfterAccess;
        long period = builder.period;
        if (period > 0) {
            cleaner.scheduleAtFixedRate(() -> {
                reduceExpiredEntries(builder.removal);
            }, period, period, TimeUnit.MILLISECONDS);
        }
    }
    public void reduceExpiredEntries(
            BiFunction<K, ? super CompletableFuture<V>, ? extends CompletableFuture<?>> reducer) {
        reduce((k, e) -> e.isExpiredNode(afterAccess)
                ? reducer.apply(k, e.val)
                : e.val);
    }
    public void reduce(BiFunction<? super K, ? super Node<V>, ? extends CompletableFuture<?>> reducer) {
        for (Map.Entry<K, Node<V>> entry : map.entrySet()) {
            Node<V> e = entry.getValue();
            CompletableFuture<?> action;
            if ((action = reducer.apply(entry.getKey(), e)) == null) {
                e.val.thenRun(() -> map.remove(entry.getKey(), e));
            } else if (action == e.val) {
                e.refresh();
            } else {
                action.thenAcceptBoth(e.val, (x,r) -> {
                    if (x != r) {
                        map.remove(entry.getKey(), e);
                    }
                });
            }
        }
    }
    public CompletableFuture<V>[] clear(
            BiFunction<? super K, ? super CompletableFuture<V>, ? extends CompletableFuture<?>> reducer) {
        Set<Map.Entry<K, Node<V>>> entrySet = map.entrySet();
        CompletableFuture<V>[] arr = new CompletableFuture[entrySet.size()];
        int i = 0;
        for (Map.Entry<K, Node<V>> entry : entrySet) {
            Node<V> e = entry.getValue();
            CompletableFuture<V> val = e.val;
            CompletableFuture<?> action = reducer.apply(entry.getKey(), val);
            arr[i++] = action == null
                    ? val.thenApply(x -> {
                map.remove(entry.getKey(), e);
                return x;
            }) : action.thenCombine(val, (x,r) -> {
                map.remove(entry.getKey(), e);
                return r;
            });
        }
        return arr;
    }
    private void expungeExpiredEntries() {
        for (Map.Entry<K, Node<V>> entry : map.entrySet()) {
            Node<V> e = entry.getValue();
            if (e.isExpiredNode(afterAccess)) {
                e.val.thenRun(() -> map.remove(entry.getKey(), e));
            }
        }
    }

    public CompletableFuture<V> computeIfAbsent(
            K key,
            Function<? super K, ? extends CompletableFuture<V>> remapping) {
        Node<V> n = map.computeIfAbsent(key, k -> {
            CompletableFuture<V> val = remapping.apply(k);
            return val == null ? null : new Node<>(val);
        });
        return n == null ? null : n.val;
    }
    public CompletableFuture<V> computeIfPresent(
            K key,
            Function<? super CompletableFuture<V>, ? extends CompletableFuture<V>> remappingFunction) {
        if (key == null || remappingFunction == null)
            throw new NullPointerException();
        for (Node<V> e;;) {
            if ((e = map.get(key)) == null) {
                return null;
            }
            CompletableFuture<V> cf = e.val,
                    newVal = remappingFunction.apply(cf);
            if (newVal == null) {
                Node<V> f = e;
                return cf.thenApply(r -> {
                    map.remove(key, f);
                    return r;
                });
            } else if (map.replace(key, e, new Node<>(newVal))) {
                return newVal;
            }
        }
    }
    public CompletableFuture<V> compute(
            K key,
            Function<? super CompletableFuture<V>, ? extends CompletableFuture<V>> remappingFunction) {
        if (key == null || remappingFunction == null)
            throw new NullPointerException();
        for (Node<V> e;;) {
            if ((e = map.get(key)) == null) {
                CompletableFuture<V> newVal = remappingFunction.apply(null);
                if (newVal == null)
                    return null;
                else if ((e = map.putIfAbsent(key,
                        new Node<>(newVal))) == null)
                    return newVal;
            }
            CompletableFuture<V> cf = e.val,
                    newVal = remappingFunction.apply(cf);
            if (newVal == null) {
                Node<V> f = e;
                return cf.thenApply(r -> {
                    map.remove(key, f);
                    return r;
                });
            } else if (map.replace(key, e, new Node<>(newVal))) {
                return newVal;
            }
        }
    }

    public CompletableFuture<V> put(K key, CompletableFuture<V> value) {
        if (key == null || value == null)
            throw new NullPointerException();
        Node<V> prev = map.put(key, new Node<>(value));
        return prev == null ? null : prev.val;
    }

    public CompletableFuture<V> remove(K key) {
        Node<V> h = map.get(key);
        if (h == null) return null;
        return h.val.thenApply(r -> {
            map.remove(key, h);
            return r;
        });
    }
    public CompletableFuture<V> get(K key) {
        return getOrDefault(key, null);
    }
    public CompletableFuture<V> getOrDefault(K key, CompletableFuture<V> defaultValue) {
        Node<V> e = map.get(key);
        if (e == null) {
            return defaultValue;
        } else {
            e.refresh();
        }
        return e.val;
    }

    private static final class Node<V> {
        static final int NESTED = -1;
        final CompletableFuture<V> val;
        volatile long lifetime
                = System.currentTimeMillis();

        Node(CompletableFuture<V> val) {
            this.val = val;
        }

        void refresh() {
            TIME.setRelease(this, System.currentTimeMillis());
        }
        boolean isExpiredNode(long expiration) {
            long now = System.currentTimeMillis();
            return (expiration != NESTED &&
                    (now - (long) TIME.getAcquire(this)) >= expiration);
        }
    }
    // VarHandle mechanics
    private static final VarHandle TIME;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            TIME = l.findVarHandle(Node.class, "lifetime", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
