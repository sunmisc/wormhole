package sunmisc.utils.lazy;

import sunmisc.utils.Scalar;

public final class SimpleLazy<V, E extends Throwable>
        implements Lazy<V,E> {
    private Scalar<V,E> scalar;
    private V result;

    public SimpleLazy(final Scalar<V,E> scalar) {
        this.scalar = scalar;
    }

    @Override
    public V value() throws E {
        V res = this.result;
        if (res == null) {
            this.result = res = this.scalar.value();
            this.scalar = null;
        }
        return res;
    }

    @Override
    public boolean completed() {
        return this.scalar == null;
    }
}
