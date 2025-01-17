package sunmisc.utils.world.tensors;

import sunmisc.utils.Cursor;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class NumberTensor implements Tensor {
    private final Number number;

    public NumberTensor(final Number number) {
        this.number = number;
    }

    @Override
    public Tensor walk(final List<Integer> indexes) {
        return Tensor.EMPTY;
    }

    @Override
    public Cursor<Number> value() {
        return new Cursor.OnceCursor<>(this.number);
    }

    @Override
    public Iterator<Tensor> iterator() {
        return Collections.emptyIterator();
    }
}
