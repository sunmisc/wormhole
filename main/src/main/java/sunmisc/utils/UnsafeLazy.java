package sunmisc.utils;

import java.io.Serial;
import java.io.Serializable;
import java.util.function.Supplier;

public class UnsafeLazy<V> extends Lazy<V>
        implements Serializable {
    @Serial
    private static final long serialVersionUID = -3248881245212313450L;
    @SuppressWarnings("unchecked")
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
