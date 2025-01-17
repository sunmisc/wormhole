package sunmisc.utils.world.tensors;

import sunmisc.utils.Cursor;

import java.util.Iterator;
import java.util.List;

public final class DeepTensor implements Tensor {
    private final List<Tensor> children;

    public DeepTensor(final List<Tensor> children) {
        this.children = children;
    }

    @Override
    public Tensor walk(final List<Integer> indexes) {
        final Tensor start = this.children.get(indexes.getFirst());
        return start.walk(indexes.subList(1, indexes.size()));
    }

    @Override
    public Cursor<Number> value() {
        return Cursor.empty();
    }

    @Override
    public Iterator<Tensor> iterator() {
        return this.children.iterator();
    }
}
