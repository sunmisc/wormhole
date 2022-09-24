package zelva.utils;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class UnsafeLazy<V> extends Lazy<V> {
    private V value;

    public UnsafeLazy(Supplier<V> supplier) {
        super(supplier);
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
        return val == NIL ? val : (value = function.apply(val));
    }
}
