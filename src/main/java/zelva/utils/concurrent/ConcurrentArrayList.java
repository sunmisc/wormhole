package zelva.utils.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentArrayList<E> {
    private static final int DEFAULT_CAPACITY = 10;
    public final ConcurrentArrayCopy<E> elements =
            new ConcurrentArrayCopy<>(DEFAULT_CAPACITY);
    public final AtomicInteger counter = new AtomicInteger();

    public void add(E element) {
        int i = counter.getAndIncrement();
        elements.set(i, element);
    }

    public void remove(int i) {
        // elements.resize(i + 1, i,elements.size()-i);
        counter.decrementAndGet();
    }
}
