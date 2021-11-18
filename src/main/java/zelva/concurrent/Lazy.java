package zelva.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Function;
import java.util.function.Supplier;

public class Lazy<V> {
    private volatile V value; // non-volatile
    private final Supplier<? extends V> supplier;

    public Lazy(Supplier<? extends V> supplier) {
        this.supplier = supplier;
    }
    @SuppressWarnings("unchecked")
    public V get() {
        return (V) VAL.getAcquire(this);
    }

    @SuppressWarnings("unchecked")
    public V getOrLoad() {
        V val;
        if ((val = get()) == null) {
            Object witness = VAL.compareAndExchange(this, null,
                    val = supplier.get());
            return witness == null ? val : (V) witness;
        }
        return val;
    }

    public V getOrDefault(V def) {
        V val;
        return (val = get()) == null ? def : val;
    }

    public <R> R compute(Function<? super V, R> function) {
        return function.apply(get());
    }

    public <R> R merge(Function<? super V, ? extends R> function, R def) {
        V val;
        return (val = get()) == null ? def : function.apply(val);
    }

    private static final VarHandle VAL;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(Lazy.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
