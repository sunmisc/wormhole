package sunmisc.utils.concurrent;

import sunmisc.utils.Lazy;

import java.io.Serial;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
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
    /*
     * on some platforms (for example, arm),
     * we have very different semantics of volatile and final,
     * after calling the constructor, we can read the value field through the race
     * and see null there (which is not a valid execution)
     * value = NIL; // not initialized
     * any other values - initialized
     * to do this, we wrap the user's null in NIL by doing a set from,
     * so null (the field's default value) is not read through the race
     * because I don’t have a normal opportunity to test it on arm,
     * maybe you shouldn’t guess with barriers and do it this way
     * the problem itself:
     * https://github.com/openjdk/jcstress/blob/master/jcstress-samples/src/main/java/org/openjdk/jcstress/samples/jmm/advanced/AdvancedJMM_13_VolatileVsFinal.java
     */

    private volatile V value;

    public ConcurrentLazy(Supplier<V> supplier) {
        super(supplier);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final V get() {
        V val;
        if ((val = value) == null) {
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
                if ((val = (V) VALUE.get(this)) == null) {
                    val = supplier.get();
                    value = decodeValue(val);
                    return val;
                }
            }
        }
        return encodeValue(val);
    }
    @Override
    public final boolean isDone() {
        return value != null;
    }
    @SuppressWarnings("unchecked")
    static <T> T encodeValue(T val) {
        return (val == NIL) ? null : val;
    }
    @SuppressWarnings("unchecked")
    static <T> T decodeValue(T val) {
        return (val == null) ? (T) NIL : val;
    }

    @Override
    public String toString() {
        final V val = value;
        return val == null ? "not initialized" : val.toString();
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConcurrentLazy<?> that = (ConcurrentLazy<?>) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
