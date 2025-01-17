package sunmisc.utils.world.tensors;

import sunmisc.utils.Cursor;
import sunmisc.utils.world.tensors.exceptions.ShapeTensorException;

import java.util.Iterator;
import java.util.List;

public final class VectorTensor implements Tensor {
    private final List<Number> vector;

    public VectorTensor(final List<Number> vector) {
        this.vector = vector;
    }

    @Override
    public Iterator<Tensor> iterator() {
        return this.vector.stream()
                .map(e -> (Tensor) new NumberTensor(e))
                .iterator();
    }

    @Override
    public Tensor walk(final List<Integer> indexes) {
        if (indexes.size() > 1) {
            throw new ShapeTensorException(new int[]{this.vector.size()});
        }
        return new NumberTensor(this.vector.get(indexes.getFirst()));
    }

    @Override
    public Cursor<Number> value() {
        return Cursor.empty();
    }
}
