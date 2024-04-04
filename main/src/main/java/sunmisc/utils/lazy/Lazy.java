package sunmisc.utils.lazy;

import java.util.function.Supplier;

public interface Lazy<E> extends Supplier<E> {

    boolean isDone();
}
