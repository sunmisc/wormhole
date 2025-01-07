package sunmisc.utils.lazy;

import sunmisc.utils.Scalar;

public interface Lazy<V> extends Scalar<V> {

    boolean completed();

}