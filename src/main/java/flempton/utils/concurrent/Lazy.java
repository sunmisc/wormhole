package flempton.utils.concurrent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lazy initialization respecting happens-before,
 * value can be any object including null
 *
 * The lock mechanism refers to the current object,
 * so we can write our own wrappers for the Lazy class
 *
 * @author ZelvaLea
 */
public class Lazy<V> {
    static final Object NIL = new Object();
    /*
     * Perhaps, one of the most surprising JMM behaviors
     * is that volatile fields do not include
     * the final field semantics. That is,
     * if we publish the reference to the object racily,
     * then we can see the null for the "volatile" field!
     *
     * It can be seen on some platforms
     */
    @SuppressWarnings("unchecked")
    private volatile V value;
    private final Supplier<V> supplier;

    public Lazy(Supplier<V> supplier) {
        this.value = (V) NIL;
        // fence ?
        this.supplier = Objects.requireNonNull(supplier);
    }
    public V get() {
        V val;
        if ((val = value) == NIL) {
            synchronized (this) {
                // no need for volatile-read here
                if ((val = value)
                        == NIL) {
                    return value = supplier.get();
                }
            }
        }
        return val;
    }
    public boolean isDone() {
        return value != NIL;
    }
}
