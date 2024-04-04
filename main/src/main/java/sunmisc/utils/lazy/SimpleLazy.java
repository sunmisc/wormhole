package sunmisc.utils.lazy;

import java.util.function.Supplier;

public class SimpleLazy<E> implements Lazy<E> {
    private Supplier<E> supplier;
    private E result;

    public SimpleLazy(Supplier<E> supplier) {
        this.supplier = supplier;
    }

    @Override
    public E get() {
        E res = result;
        if (res == null) {
            result = res = supplier.get();
            supplier = null;
        }
        return res;
    }

    @Override
    public boolean isDone() {
        return supplier == null;
    }
}
