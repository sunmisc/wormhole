package sunmisc.utils.concurrent.memory;

import java.util.function.UnaryOperator;

public interface ModifiableMemory<E> extends ReadableMemory<E> {

    E fetchAndStore(int index, E value) throws IndexOutOfBoundsException;

    E compareAndExchange(int index,
                         E expectedValue,
                         E newValue
    ) throws IndexOutOfBoundsException;

    default boolean compareAndStore(final int index,
                    final E expectedValue,
                    final E newValue
    ) throws IndexOutOfBoundsException {
        return this.compareAndExchange(index,
                expectedValue,
                newValue
        ) == expectedValue;
    }

    default void store(
            final int index,
            final E value
    ) throws IndexOutOfBoundsException {
        this.fetchAndStore(index, value);
    }

    ModifiableMemory<E> realloc(int size) throws OutOfMemoryError;

    default void transform(final int index,
                           final UnaryOperator<E> operator
    ) throws IndexOutOfBoundsException {
        for (E current; !this.compareAndStore(index,
                current = this.fetch(index),
                operator.apply(current)
             );) {
        }
    }
}
