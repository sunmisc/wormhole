package sunmisc.utils.concurrent.lists;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AtomicInteger size;
    private E[] elements;

    public ConcurrentArrayList() {
        this(DEFAULT_CAPACITY);
    }
    @SuppressWarnings("unchecked")
    public ConcurrentArrayList(final int initialCapacity) {
        this.elements = (E[]) new Object[Math.max(1, initialCapacity)];
        this.size = new AtomicInteger(0);
    }
    @SuppressWarnings("unchecked")
    public ConcurrentArrayList(final Collection<? extends E> c) {
        this((E[]) c.toArray());
    }
    // for subList
    private ConcurrentArrayList(final E[] elements) {
        this.elements = elements;
        this.size = new AtomicInteger(elements.length);
    }

    @Override
    public void forEach(final Consumer<? super E> action) {
        Objects.requireNonNull(action);
        long stamp = this.lock.tryOptimisticRead();
        try {
            int i = 0;
            for (;; stamp = this.lock.readLock()) {
                if (stamp == 0L) {
                    continue;
                }
                for (;;) {
                    final int n = this.size.getPlain();
                    final E[] es = this.elements;
                    final E val = i < n ? es[i] : null;
                    if (this.lock.validate(stamp)) {
                        if (i++ >= n) {
                            return;
                        }
                        action.accept(val);
                    } else {
                        break;
                    }
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp)) {
                this.lock.unlockRead(stamp);
            }
        }
    }

    /*
     * Reading sequentially coordinated, it was possible to do index
     * checking immediately, but we have the option to wait for a write
     * (if read is accessed while writing), this is fully consistent
     * with the semantics of synchronizedList.
     */
    @Override
    public E get(final int index) {
        long stamp = this.lock.tryOptimisticRead();
        try {
            for (;; stamp = this.lock.readLock()) {
                if (stamp == 0L) {
                    continue;
                }
                final int n = this.size.getPlain();
                final E[] es = this.elements;
                final E val = index < n ? es[index] : null;
                if (this.lock.validate(stamp)) {
                    Objects.checkIndex(index, n);
                    return val;
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp)) {
                this.lock.unlockRead(stamp);
            }
        }
    }

    @Override
    public E getFirst() {
        long stamp = this.lock.tryOptimisticRead();
        try {
            for (;; stamp = this.lock.readLock()) {
                if (stamp == 0L) {
                    continue;
                }
                final int n = this.size.getPlain();
                final E[] es = this.elements;
                final E val = es[0];
                if (this.lock.validate(stamp)) {
                    if (n == 0) {
                        throw new NoSuchElementException();
                    }
                    return val;
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp)) {
                this.lock.unlockRead(stamp);
            }
        }
    }

    @Override
    public E getLast() {
        long stamp = this.lock.tryOptimisticRead();
        try {
            for (;; stamp = this.lock.readLock()) {
                if (stamp == 0L) {
                    continue;
                }
                final int n = this.size.getPlain();
                final int index = n - 1;
                final E[] es = this.elements;
                final E val = index < n && index >= 0 ? es[index] : null;
                if (this.lock.validate(stamp)) {
                    if (n == 0) {
                        throw new NoSuchElementException();
                    }
                    return val;
                }
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp)) {
                this.lock.unlockRead(stamp);
            }
        }
    }

    @Override
    public boolean add(final E e) {
        Objects.requireNonNull(e);
        final long stamp = this.lock.writeLock();
        try {
            final int index = this.size.getPlain();
            final int n = index + 1;
            E[] es = this.elements;
            if (es.length <= index) {
                this.elements = es = allocateNextArray(es, n);
            }
            es[index] = e;
            this.size.setRelease(n);
        } finally {
            this.lock.unlockWrite(stamp);
        }
        return true;
    }

    @Override
    public E set(final int index, final E element) {
        final long stamp = this.lock.writeLock();
        try {
            Objects.checkIndex(index, this.size.getPlain());
            final E oldValue = this.elements[index];
            this.elements[index] = element;
            return oldValue;
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(final int index, final E element) {
        final long stamp = this.lock.writeLock();
        try {
            final int s = this.size.getPlain();
            final int n = s + 1;
            Objects.checkIndex(index, n);
            E[] es = this.elements;
            if (s == es.length) {
                this.elements = es = allocateNextArray(es, n);
            }
            System.arraycopy(es, index,
                    es, index + 1,
                    s - index);
            es[index] = element;
            this.size.setRelease(n);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }
    @Override
    public E remove(final int index) {
        final long stamp = this.lock.writeLock();
        try {
            Objects.checkIndex(index, this.size.getPlain());
            return fastRemove(index);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }
    private E fastRemove(final int index) {
        assert this.lock.isWriteLocked();
        final E[] es = this.elements;
        final E oldVal = es[index];
        final int newSize = this.size.getPlain() - 1;
        if (newSize > index) {
            System.arraycopy(es, index + 1, es, index, newSize - index);
        }
        es[newSize] = null;
        this.size.setRelease(newSize);
        return oldVal;
    }
    @Override
    public boolean remove(final Object o) {
        final long stamp = this.lock.writeLock();
        try {
            final int i = indexOfRange(o, 0, this.size.getPlain());
            if (i < 0) {
                return false;
            }
            fastRemove(i);
            return true;
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }
    @Override
    public void clear() {
        final long stamp = this.lock.writeLock();
        try {
            // order is not important
            this.size.setRelease(0);
            // help gc
            Arrays.fill(this.elements, null);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public int size() {
        return size.getAcquire();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(final Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(final Object o) {
        final long stamp = this.lock.readLock();
        try {
            return indexOfRange(o, 0, this.size.getPlain());
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public int lastIndexOf(final Object o) {
        final long stamp = this.lock.readLock();
        try {
            return lastIndexOfRange(o, 0, this.size.getPlain());
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    private int indexOfRange(final Object o, final int start, final int end) {
        final E[] es = this.elements;
        for (int i = start; i < end; i++) {
            if (Objects.equals(es[i], o)) {
                return i;
            }
        }
        return -1;
    }
    private int lastIndexOfRange(final Object o, final int start, final int end) {
        final E[] es = this.elements;
        for (int i = end - 1; i >= start; i--) {
            if (Objects.equals(es[i], o)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void sort(final Comparator<? super E> c) {
        Objects.requireNonNull(c);
        final long stamp = this.lock.writeLock();
        try {
            Arrays.sort(this.elements, 0, this.size.getPlain(), c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public Object[] toArray() {
        final long stamp = this.lock.readLock();
        try {
            return Arrays.copyOf(this.elements, this.size.getPlain());
        } finally {
            this.lock.unlockRead(stamp);
        }
    }
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(final T[] a) {
        final long stamp = this.lock.readLock();
        try {
            final int n = this.size.getPlain();
            if (a.length < n) {
                return (T[]) Arrays.copyOf(this.elements, n, a.getClass());
            }
            System.arraycopy(this.elements, 0, a, 0, n);
            if (a.length > n) {
                a[n] = null;
            }
            return a;
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        Objects.requireNonNull(c);
        final long stamp = this.lock.readLock();
        try {
            final int n = this.size.getPlain();
            for (final Object e : c) {
                if (indexOfRange(e, 0, n) < 0) {
                    return false;
                }
            }
        } finally {
            this.lock.unlockRead(stamp);
        }
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty()) {
            return false;
        }
        final long stamp = this.lock.writeLock();
        try {
            addAll0(this.size.getPlain(), c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
        return true;
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends E> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty()) {
            return false;
        }
        final long stamp = this.lock.writeLock();
        try {
            addAll0(index, c);
        } finally {
            this.lock.unlockWrite(stamp);
        }
        return true;
    }
    private void addAll0(final int index, final Collection<? extends E> c) {
        assert lock.isWriteLocked();
        final int ts = c.size();
        E[] es = this.elements;
        final int s = this.size.getPlain();
        final int newSize = s + ts;
        if (ts > es.length - s) {
            this.elements = es = allocateNextArray(es, newSize);
        }
        final int numMoved = s - index;
        if (numMoved > 0) {
            System.arraycopy(es, index,
                    es, index + ts,
                    numMoved);
        }
        int i = index;
        for (final E e : c) {
            es[i++] = e;
        }
        this.size.setRelease(newSize);
    }


    @Override
    public boolean removeAll(final Collection<?> c) {
        Objects.requireNonNull(c);
        final long stamp = this.lock.writeLock();
        try {
            return batchRemove(c, false);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeIf(final Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        final long stamp = this.lock.writeLock();
        try {
            final E[] es = this.elements;
            // Optimize for initial run of survivors
            int i = 0;
            final int end = this.size.getPlain();
            for (; i < end && !filter.test(es[i]); i++);
            // Tolerate predicates that reentrantly access the collection for
            // read (but writers still get CME), so traverse once to find
            // elements to delete, a second pass to physically expunge.
            if (i < end) {
                final int beg = i;
                final long[] deathRow = nBits(end - beg);
                deathRow[0] = 1L;   // set bit 0
                for (i = beg + 1; i < end; i++) {
                    if (filter.test(es[i])) {
                        setBit(deathRow, i - beg);
                    }
                }
                int w = beg;
                for (i = beg; i < end; i++) {
                    if (isClear(deathRow, i - beg)) {
                        es[w++] = es[i];
                    }
                }
                shiftTailOverGap(es, w, end);
                return true;
            } else {
                return false;
            }
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    // A tiny bit set implementation
    private static long[] nBits(final int n) {
        return new long[((n - 1) >> 6) + 1];
    }
    private static void setBit(final long[] bits, final int i) {
        bits[i >> 6] |= 1L << i;
    }
    private static boolean isClear(final long[] bits, final int i) {
        return (bits[i >> 6] & (1L << i)) == 0;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        Objects.requireNonNull(c);
        final long stamp = this.lock.writeLock();
        try {
            return batchRemove(c, true);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public void replaceAll(final UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        final long stamp = this.lock.writeLock();
        try {
            final E[] es = this.elements;
            for (int i = 0, n = this.size.getPlain(); i < n; ++i) {
                es[i] = operator.apply(es[i]);
            }
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    private boolean batchRemove(final Collection<?> c, final boolean complement) {
        Objects.requireNonNull(c);
        assert this.lock.isWriteLocked();
        final E[] es = this.elements;
        final int end = this.size.getPlain();
        int r;
        // Optimize for initial run of survivors
        for (r = 0;; r++) {
            if (r == end) {
                return false;
            } else if (c.contains(es[r]) != complement) {
                break;
            }
        }
        int w = r++;
        try {
            for (; r < end; r++) {
                final E e = es[r];
                if (c.contains(e) == complement) {
                    es[w++] = e;
                }
            }
        } finally {
            shiftTailOverGap(es, w, end);
        }
        return true;
    }
    private void shiftTailOverGap(final E[] es, final int lo, final int hi) {
        final int newSize = hi - (hi - lo);
        Arrays.fill(es, newSize, hi, null);
        this.size.setRelease(newSize);
    }
    private E[] allocateNextArray(final E[] oldArray, final int minCapacity) {
        final int oldCapacity = oldArray.length;
        final int newCapacity = Math.max(
                            minCapacity,
                            oldCapacity + 1
        );
        return Arrays.copyOf(oldArray, newCapacity);
    }

    @Override
    public List<E> subList(final int fromIndex, final int toIndex) {
        final long stamp = this.lock.readLock();
        try {
            Objects.checkFromToIndex(fromIndex, toIndex, this.size.getPlain());
            // to prevent memory leaks (by holding the current list)
            // we honestly copy (I was also too lazy to implement another list)
            final E[] es = Arrays.copyOfRange(this.elements, fromIndex, toIndex);
            return new ConcurrentArrayList<>(es);
        } finally {
            this.lock.unlockRead(stamp);
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
    public ListIterator<E> listIterator(final int index) {
        return new Itr<>(this, index);
    }

    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this,
                Spliterator.ORDERED |
                        Spliterator.NONNULL |
                        Spliterator.CONCURRENT
        );
    }

    public static class Itr<E> implements ListIterator<E> {
        private final ConcurrentArrayList<E> list;
        private E current, prev;
        private int index, lastRet = -1;

        public Itr(final ConcurrentArrayList<E> list, final int index) {
            this.list = list;
            this.index = index;
            advance(true);
        }

        @Override
        public boolean hasNext() {
            return this.current != null;
        }

        @Override
        public boolean hasPrevious() {
            return this.prev != null;
        }

        @Override
        public E next() {
            final E e = this.current;
            if (e == null) {
                throw new NoSuchElementException();
            }
            this.lastRet = this.index++;
            advance(false);
            return e;
        }
        @Override
        public E previous() {
            final E e = this.prev;
            if (e == null) {
                throw new NoSuchElementException();
            }
            this.lastRet = this.index--;
            advance(false);
            return e;
        }

        @Override
        public int nextIndex() {
            return this.index;
        }

        @Override
        public int previousIndex() {
            return this.index - 1;
        }

        @Override
        public void remove() {
            final int i = this.lastRet;
            if (i < 0) {
                throw new IllegalStateException();
            }
            try {
                this.list.remove(i);
            } finally {
                this.lastRet = -1;
            }
        }

        @Override
        public void set(final E e) {
            final int i = this.lastRet;
            if (i < 0) {
                throw new IllegalStateException();
            }
            try {
                this.list.set(i, e);
            } finally {
                this.lastRet = -1;
            }
        }

        @Override
        public void add(final E e) {
            final int i = this.lastRet;
            if (i < 0) {
                throw new IllegalStateException();
            }
            try {
                this.list.add(i, e);
            } finally {
                this.lastRet = -1;
            }
        }
        private void advance(final boolean checkOutOfBoundsIndex) {
            final int i = this.index, p = i - 1;
            final StampedLock lock = this.list.lock;
            long stamp = lock.tryOptimisticRead();
            try {
                for (;; stamp = lock.readLock()) {
                    if (stamp == 0L) {
                        continue;
                    }
                    final int n = this.list.size.getPlain();
                    final E[] es = this.list.elements;
                    final E next = i < n ? es[i] : null;
                    final E prev = p >= 0 && p < n ? es[p] : null;
                    if (lock.validate(stamp)) {
                        if (checkOutOfBoundsIndex) {
                            Objects.checkIndex(i, n);
                        }
                        this.current = next;
                        this.prev = prev;
                        break;
                    }
                }
            } finally {
                if (StampedLock.isReadLockStamp(stamp)) {
                    lock.unlockRead(stamp);
                }
            }
        }
    }
}
