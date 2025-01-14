package sunmisc.utils.lazy;

import sunmisc.utils.Scalar;

public interface Lazy<V, E extends Throwable> extends Scalar<V, E> {

    boolean completed();

}