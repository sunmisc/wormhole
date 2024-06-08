package sunmisc.utils;

@FunctionalInterface
public interface Scalar<V, E extends Throwable> {

    V value() throws E;
}
