package sunmisc.utils;

@FunctionalInterface
public interface Scalar<V> {

    V value() throws Exception;
}
