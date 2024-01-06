package sunmisc.utils.concurrent.memory;

public interface BitwiseModifiableMemory<E extends Number>
        extends ModifiableMemory<E> {

    E getAndBitwiseOr(int index, E mask);

    E getAndBitwiseAnd(int index, E mask);

    E getAndBitwiseXor(int index, E mask);
}
