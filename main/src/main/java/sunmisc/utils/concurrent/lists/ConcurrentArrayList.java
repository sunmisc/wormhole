package sunmisc.utils.concurrent.lists;

import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
        extends AbstractList<E>
        implements List<E>, RandomAccess, Serializable {
    @Serial
    private static final long serialVersionUID = 6746284661999574553L;
    private static final int DEFAULT_CAPACITY = 0;
    private final StampedLock lock = new StampedLock();
    private int size;
    private E[] elements;

    public ConcurrentArrayList() {
        this(DEFAULT_CAPACITY);
    }
    @SuppressWarnings("unchecked")
    public ConcurrentArrayList(int initialCapacity) {
        elements = (E[]) new Object[Math.max(1, initialCapacity)];
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
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        long stamp = lock.tryOptimisticRead();
        try {
            int i = 0;
            for (;; stamp = lock.readLock()) {
                if (stamp == 0L) continue;
                for (;;) {
                    final int n = size;
                    final E[] es = elements;
                    final E val = i < n ? es[i] : null;
                    if (lock.validate(stamp)) {
                        if (i++ >= n)
                            return;
                        action.accept(val);
                    } else
                        break;
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp))
                lock.unlockRead(stamp);
        }
    }

    /*
     * Reading sequentially coordinated, it was possible to do index
     * checking immediately, but we have the option to wait for a write
     * (if read is accessed while writing), this is fully consistent
     * with the semantics of synchronizedList.
     */
    @Override
    public E get(int index) {
        long stamp = lock.tryOptimisticRead();
        try {
            for (;; stamp = lock.readLock()) {
                if (stamp == 0L) continue;
                final int n = size;
                final E[] es = elements;
                final E val = index < n ? es[index] : null;
                if (lock.validate(stamp)) {
                    Objects.checkIndex(index, n);
                    return val;
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp))
                lock.unlockRead(stamp);
        }
    }

    @Override
    public E getFirst() {
        long stamp = lock.tryOptimisticRead();
        try {
            for (;; stamp = lock.readLock()) {
                if (stamp == 0L) continue;
                final int n = size;
                final E[] es = elements;
                final E val = es[0];

                if (lock.validate(stamp)) {
                    if (n == 0) throw new NoSuchElementException();
                    return val;
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp))
                lock.unlockRead(stamp);
        }
    }

    @Override
    public E getLast() {
        long stamp = lock.tryOptimisticRead();
        try {
            for (;; stamp = lock.readLock()) {
                if (stamp == 0L) continue;
                final int n = size, index = n - 1;
                final E[] es = elements;

                final E val = index < n && index >= 0 ? es[index] : null;

                if (lock.validate(stamp)) {
                    if (n == 0) throw new NoSuchElementException();
                    return val;
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp))
                lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);
        final long stamp = lock.writeLock();
        try {
            int index = size, n = index + 1;
            E[] es = elements;
            if (es.length <= index)
                elements = es = allocateNextArray(es, n);
            es[index] = e;
            updateSize(n);
        } finally {
            lock.unlockWrite(stamp);
        }
        return true;
    }

    @Override
    public E set(int index, E element) {
        final long stamp = lock.writeLock();
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
        final long stamp = lock.writeLock();
        try {
            final int s = size, n = s + 1;
            Objects.checkIndex(index, n);
            E[] es = elements;
            if (s == es.length)
                elements = es = allocateNextArray(es, n);
            System.arraycopy(es, index,
                    es, index + 1,
                    s - index);
            es[index] = element;

            updateSize(n);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    @Override
    public E remove(int index) {
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

    private void updateSize(int v) {
        SIZE.setRelease(this, v);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
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

    private int indexOfRange(Object o, int start, int end) {
        final E[] es = elements;
        for (int i = start; i < end; i++) {
            if (Objects.equals(es[i], o))
                return i;
        }
        return -1;
    }
    private int lastIndexOfRange(Object o, int start, int end) {
        final E[] es = elements;
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
            final int n = size;
            if (a.length < n)
                return (T[]) Arrays.copyOf(elements, n, a.getClass());
            System.arraycopy(elements, 0, a, 0, n);
            if (a.length > n)
                a[n] = null;
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
            final int n = size;
            for (Object e : c) {
                if (indexOfRange(e, 0, n) < 0)
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

        E[] es = elements;
        final int s = size, newSize = s + ts;

        if (ts > es.length - s)
            elements = es = allocateNextArray(es, newSize);
        int numMoved = s - index;
        if (numMoved > 0)
            System.arraycopy(es, index,
                    es, index + ts,
                    numMoved);
        int i = index;
        for (E e : c)
            es[i++] = e;
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
            final E[] es = elements;
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
    private void shiftTailOverGap(E[] es, int lo, int hi) {
        final int newSize = hi - (hi - lo);
        Arrays.fill(es, newSize, hi, null);
        updateSize(newSize);
    }
    private E[] allocateNextArray(E[] oldArray, int minCapacity) {
        int oldCapacity = oldArray.length,
            newCapacity = Math.max(
                    minCapacity,
                    oldCapacity + 1);
        return Arrays.copyOf(oldArray, newCapacity);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        final long stamp = lock.readLock();
        try {
            Objects.checkFromToIndex(fromIndex, toIndex, size);

            // to prevent memory leaks (by holding the current list)
            // we honestly copy (I was also too lazy to implement another list)
            E[] es = Arrays.copyOfRange(elements, fromIndex, toIndex);

            return new ConcurrentArrayList<>(es);
        } finally {
            lock.unlockRead(stamp);
        }
    }
    @Override
    public Iterator<E> iterator() {
        return listIterator();
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new Itr<>(this, index);
    }

    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this,
                Spliterator.ORDERED |
                        Spliterator.NONNULL |
                        Spliterator.CONCURRENT);
    }

    public static class Itr<E> implements ListIterator<E> {
        private final ConcurrentArrayList<E> list;
        private E current, prev;
        private int index, lastRet = -1;

        public Itr(ConcurrentArrayList<E> list, int index) {
            this.list = list;
            this.index = index;
            advance(true);
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public boolean hasPrevious() {
            return prev != null;
        }

        @Override
        public E next() {
            final E e = current;
            if (e == null) throw new NoSuchElementException();
            lastRet = index++;
            advance(false);
            return e;
        }
        @Override
        public E previous() {
            final E e = prev;
            if (e == null) throw new NoSuchElementException();
            lastRet = index--;
            advance(false);
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
            if (i < 0)
                throw new IllegalStateException();
            try {
                list.remove(i);
            } finally {
                lastRet = -1;
            }
        }

        @Override
        public void set(E e) {
            final int i = lastRet;
            if (i < 0)
                throw new IllegalStateException();
            try {
                list.set(i, e);
            } finally {
                lastRet = -1;
            }
        }

        @Override
        public void add(E e) {
            final int i = lastRet;
            if (i < 0)
                throw new IllegalStateException();
            try {
                list.add(i, e);
            } finally {
                lastRet = -1;
            }
        }
        private void advance(boolean checkOutOfBoundsIndex) {
            final int i = index, p = i - 1;
            StampedLock lock = list.lock;
            long stamp = lock.tryOptimisticRead();
            try {
                for (;; stamp = lock.readLock()) {
                    if (stamp == 0L) continue;
                    final int n = list.size;
                    final E[] es = list.elements;

                    final E next = i < n ? es[i] : null;
                    final E prev = p >= 0 && p < n ? es[p] : null;

                    if (lock.validate(stamp)) {
                        if (checkOutOfBoundsIndex)
                            Objects.checkIndex(i, n);
                        this.current = next;
                        this.prev = prev;
                        break;
                    }
                }
            } finally {
                if (StampedLock.isReadLockStamp(stamp))
                    lock.unlockRead(stamp);
            }
        }
    }
    private static final VarHandle SIZE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SIZE = l.findVarHandle(ConcurrentArrayList.class,
                    "size", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
