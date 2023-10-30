package sunmisc.utils;

import sunmisc.utils.concurrent.ConcurrentLazy;

import java.util.function.Supplier;

public abstract class Lazy<V> {
    protected static final Object NIL = new Object();
    protected final Supplier<V> supplier;

    protected Lazy(Supplier<V> supplier) {
        this.supplier = supplier;
    }

    public abstract V get();

    public abstract boolean isDone();


    public static <V> Lazy<V> lazyOf(LazyType type, Supplier<V> supplier) {
        return type.create(supplier);
    }


    public enum LazyType {
        CONCURRENT {
            @Override
            <V> Lazy<V> create(Supplier<V> supplier) {
                return ConcurrentLazy.of(supplier);
            }
        },
        UNSAFE {
            @Override
            <V> Lazy<V> create(Supplier<V> supplier) {
                return new UnsafeLazy<>(supplier);
            }
        };

        abstract <V> Lazy<V> create(Supplier<V> supplier);
    }
}
