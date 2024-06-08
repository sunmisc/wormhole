package sunmisc.utils.lazy;

import sunmisc.utils.Scalar;

public final class SimpleLazy<V, E extends Throwable>
        implements Lazy<V,E> {
    private Scalar<V,E> scalar;
    private V result;

    public SimpleLazy(Scalar<V,E> scalar) {
        this.scalar = scalar;
    }

    @Override
    public V value() throws E {
        V res = result;
        if (res == null) {
            result = res = scalar.value();
            scalar = null;
        }
        return res;
    }

    @Override
    public boolean completed() {
        return scalar == null;
    }
}
