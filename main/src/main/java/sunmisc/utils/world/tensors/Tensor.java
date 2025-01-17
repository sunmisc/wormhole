package sunmisc.utils.world.tensors;

import sunmisc.utils.Cursor;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public interface Tensor extends Iterable<Tensor> {

    Tensor EMPTY = new Empty();

    Tensor walk(final List<Integer> indexes);

    Cursor<Number> value();

    final class Empty implements Tensor {

        @Override
        public Tensor walk(final List<Integer> indexes) {
            throw new NoSuchElementException();
        }

        @Override
        public Cursor<Number> value() {
            return Cursor.empty();
        }

        @Override
        public Iterator<Tensor> iterator() {
            return Collections.emptyIterator();
        }
    }
}
