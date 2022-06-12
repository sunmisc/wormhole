package zelva.utils;

import java.util.Arrays;

public class CellsArray<E> implements Cells<E> {
    private E[] array;

    public CellsArray(int len) {
        this.array = (E[]) new Object[len];
    }

    @Override
    public E get(int i) {
        return array[i];
    }

    @Override
    public E set(int i, E val) {
        E o = array[i];
        array[i] = val;
        return o;
    }

    @Override
    public E remove(int i) {
        return set(i, null);
    }

    @Override
    public boolean cas(int i, E c, E v) {
        if (array[i] != c)
            return false;
        array[i] = v;
        return true;
    }

    @Override
    public void resize(int size) {
        array = Arrays.copyOf(array, size);
    }

    @Override
    public int size() {
        return array.length;
    }
    @Override
    public String toString() {
        return Arrays.toString(array);
    }
}
