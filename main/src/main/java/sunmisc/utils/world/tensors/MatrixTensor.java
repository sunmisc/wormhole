package sunmisc.utils.world.tensors;

import sunmisc.utils.Cursor;
import sunmisc.utils.world.tensors.exceptions.ShapeTensorException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class MatrixTensor implements Tensor {
    private final Number[][] matrix;

    public MatrixTensor(final Number[][] matrix) {
        this.matrix = matrix;
    }

    @Override
    public Tensor walk(final List<Integer> indexes) {
        if (indexes.size() > 2) {
            throw new ShapeTensorException(new int[]{
                    this.matrix.length,
                    this.matrix[0].length
            });
        }
        final int row = indexes.getFirst();
        final int col = indexes.getLast();
        return new NumberTensor(this.matrix[row][col]);
    }

    @Override
    public Cursor<Number> value() {
        return Cursor.empty();
    }

    @Override
    public Iterator<Tensor> iterator() {
        return new Cursor.CursorAsIterator<>(new Rows(this.matrix, 0));
    }

    private record Rows(Number[][] matrix, int row) implements Cursor<Tensor> {

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Tensor element() {
            return new VectorTensor(Arrays.asList(this.matrix[this.row]));
        }

        @Override
        public Cursor<Tensor> next() {
            return this.row < this.matrix.length - 1
                    ? new Rows(this.matrix, this.row + 1)
                    : Cursor.empty();
        }
    }
}
