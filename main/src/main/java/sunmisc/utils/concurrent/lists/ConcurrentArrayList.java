package sunmisc.utils.concurrent.lists;

import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 *
 * A thread-safe variant of {@link java.util.ArrayList}
 * This implementation is intermediate between
 * <p>fast {@code get} ({@link java.util.concurrent.CopyOnWriteArrayList})
 * <p>and fast write {@link Collections#synchronizedList Collections.synchronizedList}}
 * <p>Reading from a list by index is possibly optimistic lock (without a lock).
 * <p>
 * This implementation uses StampedLock
 * For safe access by index and for a quick indicator of size, atomic size is used
 * inside the lock it can be read as plain, but still write release for safe reading outside the lock,
 * <p>
 * The iterator works like in ArrayList ({@link ListIterator#add}, {@link ListIterator#set}, {@link ListIterator#remove})
 * except that the iterator in this implementation will never throw a {@code ConcurrentModificationException}
 *
 * <p>Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * {@code ConcurrentArrayList}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions after the access or removal of that element from
 * the {@code ConcurrentArrayList} in another thread.
 *
 * @author Sunmisc Unsafe
 * @param <E> the type of elements in this list
 */
public class ConcurrentArrayList<E>
        implements List<E>, RandomAccess, Serializable {
    @Serial
    private static final long serialVersionUID = 6746284661999574553L;
    private static final int DEFAULT_CAPACITY = 10;

    private static final int SPINS = 1 << 8;
    private final StampedLock lock = new StampedLock();
    private int size;
    private E[] elements;

    public ConcurrentArrayList() {
        this(DEFAULT_CAPACITY);
    }
    @SuppressWarnings("unchecked")
    public ConcurrentArrayList(int initialCapacity) {
        initialCapacity = Math.max(1, initialCapacity);
        elements = (E[]) new Object[initialCapacity];
    }

    @SuppressWarnings("unchecked")
    public ConcurrentArrayList(Collection<? extends E> c) {
        this((E[]) c.toArray());
    }
    // for subList
    private ConcurrentArrayList(E[] elements) {
        this.elements = elements;
        this.size = elements.length;
    }

    @Override
    public E get(int index) {
        return readLock(() -> {
            Objects.checkIndex(index, size());
            // VarHandle.loadLoadFence();
            return elementsAsOpaque()[index];
        });
    }

    @Override
    public E getFirst() {
        return readLock(() -> {
            if (size() == 0) throw new NoSuchElementException();
            // VarHandle.loadLoadFence();
            return elementsAsOpaque()[0];
        });
    }

    @Override
    public E getLast() {
        return readLock(() -> {
            final int s = size();
            if (s == 0) throw new NoSuchElementException();
            // VarHandle.loadLoadFence();
            return elementsAsOpaque()[s - 1];
        });
    }
    private <R> R readLock(Supplier<R> supplier) {
        int spin = Thread.currentThread().isVirtual() ? 1 : SPINS;
        final StampedLock sl = lock;
        long stamp = 0;
        try {
            for (; ;) {
                if (spin == 0)
                    stamp = sl.readLock();
                else {
                    --spin;
                    stamp = sl.tryOptimisticRead();
                }
                if (stamp != 0) {
                    R result = supplier.get();
                    if (sl.validate(stamp))
                        return result;
                }
                Thread.onSpinWait();
            }
        } finally {
            if (spin == 0)
                sl.unlockRead(stamp);
        }
    }

    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);
        final long stamp = lock.writeLock();
        try {
            int index = size, n = index + 1;
            E[] arr = elements;
            if (arr.length <= index) {
                arr = allocateNextArray(arr, n);
                updateArray(arr);
                // StoreStoreFence
            }
            arr[index] = e;
            updateSize(n);
        } finally {
            lock.unlock(stamp);
        }
        return true;
    }

    @Override
    public E set(int index, E element) {
        Objects.checkIndex(index, size());
        long stamp = lock.writeLock();
        try {
            Objects.checkIndex(index, size);

            E oldValue = elements[index];
            elements[index] = element;
            return oldValue;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(int index, E element) {
        long stamp = lock.writeLock();
        try {
            Objects.checkIndex(index, size + 1);
            addTo(index, element);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    private void addTo(int index, E element) {
        final int s = size, n = s + 1;
        E[] es = elements;
        if (s == es.length) {
            es = allocateNextArray(es, n);
            updateArray(es);
            // StoreStoreFence
        }
        System.arraycopy(es, index,
                es, index + 1,
                s - index);
        es[index] = element;

        updateSize(n);
    }

    @Override
    public E remove(int index) {
        Objects.checkIndex(index, size());
        final long stamp = lock.writeLock();
        try {
            Objects.checkIndex(index, size);
            return fastRemove(index);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    private E fastRemove(int index) {
        assert lock.isWriteLocked();

        E[] es = elements;
        E oldVal = es[index];
        final int newSize = size - 1;
        if (newSize > index)
            System.arraycopy(es, index + 1, es, index, newSize - index);
        es[newSize] = null;
        updateSize(newSize);
        return oldVal;
    }
    @Override
    public boolean remove(Object o) {
        final long stamp = lock.writeLock();
        try {
            int i = indexOfRange(o, 0, size);
            if (i < 0)
                return false;
            fastRemove(i);
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    @Override
    public void clear() {
        final long stamp = lock.writeLock();
        try {
            // order is not important
            updateSize(0);
            // help gc
            Arrays.fill(elements, null);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public int size() {
        return (int) SIZE.getAcquire(this);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    private void updateSize(int v) {
        SIZE.setOpaque(this, v); // setPlain?
    }
    private void updateArray(E[] v) {
        ARRAY.setRelease(this, v);
    }

    @SuppressWarnings("unchecked")
    private E[] elementsAsOpaque() {
        return (E[]) ARRAY.getOpaque(this);
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public Iterator<E> iterator() {
        return new ListItr<>(this, 0);
    }

    @Override
    public int indexOf(Object o) {
        final long stamp = lock.readLock();
        try {
            return indexOfRange(o, 0, size);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public int lastIndexOf(Object o) {
        final long stamp = lock.readLock();
        try {
            return lastIndexOfRange(o, 0, size);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public ListIterator<E> listIterator() {
        return new ListItr<>(this, 0);
    }

    int indexOfRange(Object o, int start, int end) {
        Object[] es = elements;
        for (int i = start; i < end; i++) {
            if (Objects.equals(es[i], o))
                return i;
        }
        return -1;
    }
    int lastIndexOfRange(Object o, int start, int end) {
        Object[] es = elements;
        for (int i = end - 1; i >= start; i--) {
            if (Objects.equals(es[i], o))
                return i;
        }
        return -1;
    }

    @Override
    public void sort(Comparator<? super E> c) {
        Objects.requireNonNull(c);

        final long stamp = lock.writeLock();
        try {
            Arrays.sort(elements, 0, size, c);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public Object[] toArray() {
        final long stamp = lock.readLock();
        try {
            return Arrays.copyOf(elements, size);
        } finally {
            lock.unlockRead(stamp);
        }
    }
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final long stamp = lock.readLock();
        try {
            int sz = size;
            if (a.length < sz)
                return (T[]) Arrays.copyOf(elements, sz, a.getClass());
            System.arraycopy(elements, 0, a, 0, sz);
            if (a.length > sz)
                a[sz] = null;
            return a;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        Objects.requireNonNull(c);
        final long stamp = lock.readLock();

        try {
            final int sz = size;
            for (Object e : c) {
                if (indexOfRange(e, 0, sz) < 0)
                    return false;
            }
        } finally {
            lock.unlockRead(stamp);
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty()) return false;
        final long stamp = lock.writeLock();
        try {
            addAll0(size, c);
        } finally {
            lock.unlockWrite(stamp);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty()) return false;
        final long stamp = lock.writeLock();
        try {
            addAll0(index, c);
        } finally {
            lock.unlockWrite(stamp);
        }
        return true;
    }
    private void addAll0(int index, Collection<? extends E> c) {
        int ts = c.size();

        E[] elementData = elements;
        final int s = size, newSize = s + ts;

        if (ts > elementData.length - s) {
            elementData = allocateNextArray(elementData, newSize);
            updateArray(elementData);
            // StoreStoreFence
        }
        int numMoved = s - index;
        if (numMoved > 0)
            System.arraycopy(elementData, index,
                    elementData, index + ts,
                    numMoved);
        int i = index;
        for (E e : c)
            elementData[i++] = e;
        updateSize(newSize);
    }


    @Override
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);

        final long stamp = lock.writeLock();
        try {
            return batchRemove(c, false);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);

        final long stamp = lock.writeLock();
        try {
            final E[] es = elements;
            // Optimize for initial run of survivors
            int i = 0, end = size;
            for (; i < end && !filter.test(es[i]); i++);
            // Tolerate predicates that reentrantly access the collection for
            // read (but writers still get CME), so traverse once to find
            // elements to delete, a second pass to physically expunge.
            if (i < end) {
                final int beg = i;
                final long[] deathRow = nBits(end - beg);
                deathRow[0] = 1L;   // set bit 0
                for (i = beg + 1; i < end; i++)
                    if (filter.test(es[i]))
                        setBit(deathRow, i - beg);
                int w = beg;
                for (i = beg; i < end; i++)
                    if (isClear(deathRow, i - beg))
                        es[w++] = es[i];
                shiftTailOverGap(es, w, end);
                return true;
            } else
                return false;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    // A tiny bit set implementation

    private static long[] nBits(int n) {
        return new long[((n - 1) >> 6) + 1];
    }
    private static void setBit(long[] bits, int i) {
        bits[i >> 6] |= 1L << i;
    }
    private static boolean isClear(long[] bits, int i) {
        return (bits[i >> 6] & (1L << i)) == 0;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);

        final long stamp = lock.writeLock();
        try {
            return batchRemove(c, true);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        final long stamp = lock.writeLock();
        try {
            E[] es = elements;
            for (int i = 0, n = size; i < n; ++i)
                es[i] = operator.apply(es[i]);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private boolean batchRemove(Collection<?> c, boolean complement) {
        Objects.requireNonNull(c);

        assert lock.isWriteLocked();

        final E[] es = elements;
        int r, end = size;
        // Optimize for initial run of survivors
        for (r = 0;; r++) {
            if (r == end)
                return false;
            else if (c.contains(es[r]) != complement)
                break;
        }
        int w = r++;
        try {
            for (; r < end; r++) {
                E e = es[r];
                if (c.contains(e) == complement)
                    es[w++] = e;
            }
        } finally {
            shiftTailOverGap(es, w, end);
        }
        return true;
    }
    private void shiftTailOverGap(Object[] es, int lo, int hi) {
        int newSize = hi - (hi - lo);
        for (int i = newSize; i < hi; i++)
            es[i] = null;
        updateSize(newSize);
    }
    private E[] allocateNextArray(E[] oldArray, int minCapacity) {
        int oldCapacity = oldArray.length,
                newCapacity = Math.max(
                        minCapacity,
                        oldCapacity + (oldCapacity >>> 1));
        return Arrays.copyOf(oldArray, newCapacity);
    }
    @Override
    public ListIterator<E> listIterator(int index) {
        Objects.checkIndex(index, size());
        return new ListItr<>(this, index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, size());
        long stamp = lock.readLock();
        try {
            int sz = size;

            Objects.checkFromToIndex(fromIndex, toIndex, sz);

            // to prevent memory leaks (by holding the current list)
            // we honestly copy (I was also too lazy to implement another list)
            E[] es = Arrays.copyOfRange(elements, fromIndex, toIndex);

            return new ConcurrentArrayList<>(es);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        long stamp = lock.readLock();
        try {
            E[] es = elements;
            for (int i = 0, n = size; i < n; ++i)
                action.accept(es[i]);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this,
                Spliterator.ORDERED |
                        Spliterator.NONNULL |
                        Spliterator.CONCURRENT);
    }

    private static class ListItr<E> implements ListIterator<E> {
        private final ConcurrentArrayList<E> list;
        private E prev, next;
        private int index, lastRet = -1;

        ListItr(ConcurrentArrayList<E> list, int startIndex) {
            this.list = list;
            this.index = startIndex;
            advance();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public boolean hasPrevious() {
            return prev != null;
        }

        @Override
        public E next() {
            E e = next;
            if (e == null) throw new NoSuchElementException();
            lastRet = ++index;
            advance();
            return e;
        }
        @Override
        public E previous() {
            E e = prev;
            if (e == null) throw new NoSuchElementException();
            lastRet = --index;
            advance();
            return e;
        }

        @Override
        public int nextIndex() {
            return index;
        }

        @Override
        public int previousIndex() {
            return index - 1;
        }

        @Override
        public void remove() {
            final int i = lastRet;
            if (i < 0) throw new IllegalStateException();
            StampedLock lock = list.lock;
            final long stamp = lock.writeLock();
            try {
                if (i < list.size)
                    list.fastRemove(i);
            } finally {
                lock.unlockWrite(stamp);
            }
            lastRet = -1;
        }

        @Override
        public void set(E e) {
            final int i = lastRet;
            if (i < 0) throw new IllegalStateException();
            StampedLock lock = list.lock;
            final long stamp = lock.writeLock();
            try {
                if (i < list.size)
                    list.elements[i] = e;
            } finally {
                lock.unlockWrite(stamp);
            }
            lastRet = -1;
        }

        @Override
        public void add(E e) {
            final int i = lastRet;
            if (i < 0) throw new IllegalStateException();
            StampedLock lock = list.lock;
            final long stamp = lock.writeLock();
            try {
                if (i < list.size)
                    list.addTo(i, e);
            } finally {
                lock.unlockWrite(stamp);
            }
            lastRet = -1;
        }

        private void advance() {
            final int i = index, p = i - 1;
            record Pair<E>(E prev, E next) { }

            final Pair<E> pair = list.readLock(() -> {
                int u = list.size();
                // VarHandle.loadLoadFence();
                final E[] es = list.elementsAsOpaque();

                final E prev = p < 0  ? null : es[p];
                final E next = i >= u ? null : es[i];

                return new Pair<>(prev, next);
            });
            next = pair.next;
            prev = pair.prev;
        }
    }
    private static final VarHandle SIZE, ARRAY;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SIZE = l.findVarHandle(ConcurrentArrayList.class,
                    "size", int.class);
            ARRAY = l.findVarHandle(ConcurrentArrayList.class,
                    "elements", Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
