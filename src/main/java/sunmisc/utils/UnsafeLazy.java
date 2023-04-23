package sunmisc.utils;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class UnsafeLazy<V> extends Lazy<V> {
    private V value = (V) NIL;

    public UnsafeLazy(Supplier<V> supplier) {
        super(supplier);
    }

    @Override
    public Optional<V> getIfPresent() {
        V val = value;
        return val == NIL ? Optional.empty() : Optional.ofNullable(val);
    }

    @Override
    public Optional<V> clear() {
        V val = value;
        value = (V) NIL;
        return val == NIL ? Optional.empty() : Optional.ofNullable(val);
    }

    @Override
    public boolean isDone() {
        return value != NIL;
    }

    @Override
    public V compute(UnaryOperator<V> function) {
        V val = value;
        return value = function.apply(val == NIL
                ? null
                : val
        );
    }

    @Override
    public V computeIfAbsent(Supplier<? extends V> function) {
        V val = value;
        return val == NIL ? value = function.get() : val;
    }

    @Override
    public V computeIfPresent(UnaryOperator<V> function) {
        V val = value;
        return val == NIL ? null : (value = function.apply(val));
    }
}
