package sunmisc.utils.concurrent;

import sunmisc.utils.Lazy;

import java.io.Serial;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

/**
 * Lazy initialization respecting happens-before,
 * value can be any object including null
 * <p>
 * The lock mechanism refers to the current object,
 * so we can write our own wrappers for the Lazy class
 *
 * @author Sunmisc Unsafe
 */
public class ConcurrentLazy<V> extends Lazy<V>
        implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = -2248881245212313449L;
    private static final VarHandle VALUE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VALUE = l.findVarHandle(ConcurrentLazy.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile V value;

    public ConcurrentLazy(Supplier<V> supplier) {
        super(supplier);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final V get() {
        V val;
        if ((val = value) == NIL) {
            synchronized (this) {
                /*
                 * quite realistically, we can read field values with weak semantics,
                 * we have guarantees that everything
                 * is safely published in synchronized blocks,
                 * and vice versa, in a synchronized block
                 * we must safely publish a value for reading outside the
                 * synchronized block, everything behind the
                 * synchronized block must be read through a strong semantics,
                 * for these readers we need a volatile write inside the lock,
                 * the CAS mechanism can be bad practice in case
                 * of high contention and the function from the supplier is quite heavy
                 */
                if ((val = (V) VALUE.get(this)) == NIL) {
                    return value = decodeValue(supplier.get());
                }
            }
        }
        return (val == null) ? (V) NIL : val;
    }
    @Override
    public final boolean isDone() {
        return value != null;
    }
    @Override
    public String toString() {
        final V val;
        return (val = value) == null
                ? "not initialized"
                : val.toString();
    }

    static <T> T decodeValue(T t) {
        return (t == NIL) ? null : t;
    }
}
