package zelva.utils.concurrent;

import zelva.utils.Lazy;

import java.lang.invoke.VarHandle;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Lazy initialization respecting happens-before,
 * value can be any object including null
 * <p>
 * The lock mechanism refers to the current object,
 * so we can write our own wrappers for the Lazy class
 *
 * @author ZelvaLea
 */
public class ConcurrentLazy<V> extends Lazy<V> {

    /*
     * Perhaps, one of the most surprising JMM behaviors
     * is that volatile fields do not include
     * the final field semantics. That is,
     * if we publish the reference to the object racily,
     * then we can see the null for the "volatile" field!
     *
     * It can be seen on some platforms
     */
    private volatile V value;

    public ConcurrentLazy(Supplier<V> supplier) {
        super(supplier);
        this.value = (V) NIL;
        // Ensure writes can't be reordered
        VarHandle.fullFence();
    }

    @Override
    public synchronized Optional<V> clear() {
        V val = value;
        if (val != NIL && val != null) {
            value = (V) NIL;
            return Optional.of(val);
        }
        return Optional.empty();
    }
    @Override
    public Optional<V> getIfPresent() {
        final V val = value;
        return val == NIL ? Optional.empty() : Optional.ofNullable(val);
    }

    @Override
    public boolean isDone() {
        return value != NIL;
    }

    @Override
    public synchronized V compute(UnaryOperator<V> function) {
        V val = value;
        return value = function.apply(val == NIL
                ? null
                : val
        );
    }

    @Override
    public V computeIfAbsent(Supplier<? extends V> function) {
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

    @Override
    public V computeIfPresent(UnaryOperator<V> function) {
        V val = value;
        if (val != NIL) {
            synchronized (this) {
                // no need for volatile-read here
                if ((val = value)
                        != NIL) {
                    return value = function.apply(val);
                }
            }
        }
        return null;
    }
}
