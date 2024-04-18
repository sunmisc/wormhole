package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.StringJoiner;

@SuppressWarnings("unchecked")
public final class ArrayMemory<E> implements ModifiableMemory<E> {

    private final E[] array;

    public ArrayMemory(int size) {
        this.array = (E[]) new Object[size];
    }

    @Override
    public int length() {
        return array.length;
    }

    @Override
    public E fetch(int index) {
        return (E) AA.getAcquire(array, index);
    }

    @Override
    public void store(int index, E value) {
        AA.setRelease(array, index, value);
    }

    @Override
    public E fetchAndStore(int index, E value) {
        return (E) AA.getAndSet(array, index, value);
    }

    @Override
    public E compareAndExchange(int index,
                                E expectedValue,
                                E newValue
    ) throws IndexOutOfBoundsException {
        return (E) AA.compareAndExchange(array, index, expectedValue, newValue);
    }

    @Override
    public boolean compareAndStore(int index,
                                   E expectedValue,
                                   E newValue
    ) throws IndexOutOfBoundsException {
        return AA.compareAndSet(array, index, expectedValue, newValue);
    }

    @Override
    public void realloc(int size) throws OutOfMemoryError {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(
                ", ", "[", "]");
        forEach(x -> joiner.add(Objects.toString(x)));
        return joiner.toString();
    }

    // VarHandle mechanics
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
}