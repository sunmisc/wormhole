package zelva.utils.concurrent;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

public abstract class ConcurrentCells<E> implements Iterable<E> {
    public abstract E get(int i);

    public abstract E set(int i, E val);

    public E remove(int i) {
        throw new UnsupportedOperationException();
    }

    public abstract E cae(int i, E c, E v);

    public abstract void resize(IntUnaryOperator resize);

    public abstract int length();

    @Override
    public int hashCode() {
        int h = 1;
        for (E e : this)
            h = 31*h + (e == null ? 0 : e.hashCode());
        return h;
    }
    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        Iterator<?> it = null;

        if (o instanceof Iterable<?> i) {
            it = i.iterator();
        } else if (o instanceof Iterator<?> i) {
            it = i;
        }
        if (it != null) {
            for (Object element : this) {
                if (!it.hasNext() || !Objects.equals(element, it.next()))
                    return false;
            }
            return !it.hasNext();
        }
        return false;
    }

    @Override
    public String toString() {
        Iterator<E> it = iterator();
        if (! it.hasNext())
            return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            E e = it.next();
            sb.append(e == this ? "(this array)" : e);
            if (! it.hasNext())
                return sb.append(']').toString();
            sb.append(',').append(' ');
        }
    }
}
