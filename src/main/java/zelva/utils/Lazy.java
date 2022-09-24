package zelva.utils;

import zelva.utils.concurrent.ConcurrentLazy;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public abstract class Lazy<V> {
    protected static final Object NIL = new Object();
    protected final Supplier<V> supplier;

    protected Lazy(Supplier<V> supplier) {
        this.supplier = supplier;
    }

    public V get() {
        return computeIfAbsent(supplier);
    }

    public void clear() {
        computeIfPresent(x -> null);
    }

    public abstract boolean isDone();

    public abstract V compute(UnaryOperator<V> function);

    public abstract V computeIfAbsent(Supplier<? extends V> function);

    public abstract V computeIfPresent(UnaryOperator<V> function);


    public static <V> Lazy<V> lazyOf(LazyType type, Supplier<V> supplier) {
        return type.create(supplier);
    }


    public enum LazyType {
        CONCURRENT {
            @Override
            <V> Lazy<V> create(Supplier<V> supplier) {
                return new ConcurrentLazy<>(supplier);
            }
        }, UNSAFE {
            @Override
            <V> Lazy<V> create(Supplier<V> supplier) {
                return new UnsafeLazy<>(supplier);
            }
        };

        abstract <V> Lazy<V> create(Supplier<V> supplier);
    }
}
