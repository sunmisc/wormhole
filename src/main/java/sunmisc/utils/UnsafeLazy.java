package sunmisc.utils;

import java.util.function.Supplier;

public class UnsafeLazy<V> extends Lazy<V> {
    private V value = (V) NIL;

    public UnsafeLazy(Supplier<V> supplier) {
        super(supplier);
    }

    @Override
    public V get() {
        V val = value;
        return val == NIL ? value = supplier.get() : val;
    }

    @Override
    public boolean isDone() {
        return value != NIL;
    }


}
