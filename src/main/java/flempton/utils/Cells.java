package flempton.utils;

public interface Cells<E> {
    E get(int i);

    E set(int i, E val);

    E remove(int i);

    E cae(int i, E c, E v);

    void resize(int size);

    int length();
}
