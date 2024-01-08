package sunmisc.utils.concurrent.memory;

public interface BitwiseModifiableMemory<E extends Number>
        extends ModifiableMemory<E> {

    E fetchAndAdd(int index, E value);

    E fetchAndBitwiseOr(int index, E mask);

    E fetchAndBitwiseAnd(int index, E mask);

    E fetchAndBitwiseXor(int index, E mask);
}
