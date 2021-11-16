package zelva.concurrent.cache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

public class CacheBuilder<K,V> {
    long expireAfterAccess = -1;
    int maxSize = 16;
    float loadFactor = 0.75F;
    long period;
    BiFunction<K, ? super CompletableFuture<V>, ? extends CompletableFuture<V>> removal;

    public CacheBuilder<K,V> expireAfterAccess(long duration, TimeUnit unit) {
        requireNonNull(unit);
        this.expireAfterAccess = unit.toMillis(duration);
        return this;
    }
    public CacheBuilder<K,V> maxSize(int maxSize) {
        this.maxSize = maxSize;
        return this;
    }
    public CacheBuilder<K,V> periodBetweenCleanups(long value, TimeUnit unit) {
        requireNonNull(unit);
        this.period = unit.toMillis(value);
        return this;
    }
    public CacheBuilder<K,V> loadFactor(float loadFactor) {
        this.loadFactor = loadFactor;
        return this;
    }
    public CacheBuilder<K,V> removal(BiFunction<K, ? super CompletableFuture<V>, ? extends CompletableFuture<V>> removal) {
        requireNonNull(removal);
        this.removal = removal;
        return this;
    }

    public ConcurrentCache<K,V> build() {
        return new ConcurrentCache<>(this);
    }
}
