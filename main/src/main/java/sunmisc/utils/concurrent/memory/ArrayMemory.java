package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

@SuppressWarnings("unchecked")
public final class ArrayMemory<E> implements ModifiableMemory<E> {
    private final E[] array;

    public ArrayMemory(final int size) {
        this((E[]) new Object[size]);
    }
    private ArrayMemory(final E[] array) {
        this.array = array;
    }

    @Override
    public int length() {
        return this.array.length;
    }

    @Override
    public E fetch(final int index) {
        return (E) AA.getAcquire(this.array, index);
    }

    @Override
    public void store(final int index, final E value) {
        AA.setRelease(this.array, index, value);
    }

    @Override
    public E fetchAndStore(final int index, final E value) {
        return (E) AA.getAndSet(this.array, index, value);
    }

    @Override
    public E compareAndExchange(final int index,
                                final E expectedValue,
                                final E newValue
    ) throws IndexOutOfBoundsException {
        return (E) AA.compareAndExchange(this.array, index, expectedValue, newValue);
    }

    @Override
    public boolean compareAndStore(final int index,
                                   final E expectedValue,
                                   final E newValue
    ) throws IndexOutOfBoundsException {
        return AA.compareAndSet(this.array, index, expectedValue, newValue);
    }

    @Override
    public ModifiableMemory<E> realloc(final int size) throws OutOfMemoryError {
        return new ArrayMemory<>(Arrays.copyOf(this.array, size));
    }

    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner(
                ", ", "[", "]");
        forEach(x -> joiner.add(Objects.toString(x)));
        return joiner.toString();
    }

    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
}