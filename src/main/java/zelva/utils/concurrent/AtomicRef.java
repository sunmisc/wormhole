package zelva.utils.concurrent;

import java.io.Serial;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicRef<V> extends AtomicReference<V> {

    @Serial
    private static final long serialVersionUID = 7851034098732779161L;

    public AtomicRef(V initialValue) {
        super(initialValue);
    }
    public AtomicRef() {}

    public V testAndCompareAndExchange(V expectedValue, V newVal) {
        V val;
        return ((val = get()) == expectedValue)
                ? compareAndExchange(val, newVal) : val;
    }
    public boolean testAndCompareAndSet(V expectedValue, V newVal) {
        return expectedValue == get() &&
                compareAndSet(expectedValue, newVal);
    }
}
