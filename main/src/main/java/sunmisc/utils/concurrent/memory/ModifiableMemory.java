package sunmisc.utils.concurrent.memory;

public interface ModifiableMemory<E> extends ReadableMemory<E> {

    void realloc(int size) throws OutOfMemoryError;

    E fetchAndStore(int index, E value)
            throws IndexOutOfBoundsException;

    E compareAndExchange(int index, E expectedValue, E newValue)
            throws IndexOutOfBoundsException;

    default boolean
    compareAndStore(int index, E expectedValue, E newValue)
            throws IndexOutOfBoundsException {
        return compareAndExchange(index,
                expectedValue,
                newValue
        ) == expectedValue;
    }

    default void
    store(int index, E value) throws IndexOutOfBoundsException {
        fetchAndStore(index, value);
    }

}
