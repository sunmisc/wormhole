package zelva.concurrent;

import java.util.concurrent.atomic.AtomicReference;

public class AtomicRef<V> extends AtomicReference<V> {

    public AtomicRef(V initialValue) {
        super(initialValue);
    }
    public AtomicRef() {}

    public V testAndCompareAndExchange(V expectedValue, V newVal) {
        V val;
        return ((val = get()) != expectedValue)
                ? val : compareAndExchange(val, newVal);
    }
    public boolean testAndCompareAndSet(V expectedValue, V newVal) {
        return expectedValue == get() &&
                compareAndSet(expectedValue, newVal);
    }
}
