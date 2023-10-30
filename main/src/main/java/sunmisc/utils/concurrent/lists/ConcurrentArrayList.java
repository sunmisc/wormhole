package sunmisc.utils.concurrent.lists;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.locks.StampedLock;

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
 * @author Sunmisc Unsafe
 * @param <E> the type of elements in this list
 */
public class ConcurrentArrayList<E>
        extends AbstractSequentialList<E>
        implements List<E>, RandomAccess {

    private static final int DEFAULT_CAPACITY = 10;
    private final StampedLock lock
            = new StampedLock();
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

    @Override
    public E get(int index) {
        long stamp = lock.tryOptimisticRead();
        try {
            for (;; stamp = lock.readLock()) {
                if (stamp == 0L)
                    continue;
                Objects.checkIndex(index, size());
                E element = elements[index];
                if (lock.validate(stamp))
                    return element;
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp))
                lock.unlockRead(stamp);
        }
    }
    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);

        long stamp = lock.writeLock();
        try {
            int index = size;
            E[] arr = elements;
            if (arr.length <= index) {
                arr = allocateNextArray(arr, index + 1);
                elements = arr;
            }
            arr[index] = e;
            SIZE.setRelease(this, index + 1);
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
        Objects.checkIndex(index, size());
        long stamp = lock.writeLock();
        try {
            Objects.checkIndex(index, size);
            addTo(index, element);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    private void addTo(int index, E element) {
        final int s = size, u = s + 1;
        E[] es = elements;
        if (s == es.length)
            es = allocateNextArray(es, u);
        System.arraycopy(es, index,
                es, index + 1,
                s - index);
        es[index] = element;

        if (s == es.length)
            elements = es;
        SIZE.setRelease(this, u);
    }

    @Override
    public E remove(int index) {
        Objects.checkIndex(index, size());
        long stamp = lock.writeLock();
        try {
            Objects.checkIndex(index, size);
            return fastRemove(index);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    private E fastRemove(int index) {
        assert lock.isWriteLocked();

        E[] es = elements; E oldVal = es[index];
        int newSize = size - 1;
        if (newSize > index)
            System.arraycopy(es, index + 1, es, index, newSize - index);
        es[newSize] = null;
        SIZE.setRelease(this, newSize);
        return oldVal;
    }
    @Override
    public boolean remove(Object o) {
        long stamp = lock.writeLock();
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
    @SuppressWarnings("unchecked")
    public void clear() {
        long stamp = lock.writeLock();
        try {
            Arrays.fill(elements, null);
            SIZE.setRelease(this, 0);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public int size() {
        return (int) SIZE.getAcquire(this);
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(Object o) {
        long stamp = lock.readLock();
        try {
            return indexOfRange(o, 0, size);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public int lastIndexOf(Object o) {
        long stamp = lock.readLock();
        try {
            return lastIndexOfRange(o, 0, size);
        } finally {
            lock.unlockRead(stamp);
        }
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

        long stamp = lock.writeLock();
        try {
            Arrays.sort(elements, 0, size, c);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public Object[] toArray() {
        long stamp = lock.readLock();
        try {
            return Arrays.copyOf(elements, size);
        } finally {
            lock.unlockRead(stamp);
        }
    }
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        long stamp = lock.readLock();
        try {
            if (a.length < size)
                return (T[]) Arrays.copyOf(elements, size, a.getClass());
            System.arraycopy(elements, 0, a, 0, size);
            if (a.length > size)
                a[size] = null;
            return a;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        Objects.requireNonNull(c);
        long stamp = lock.readLock();

        try {
            for (Object e : c) {
                if (contains(e))
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
        if (c.isEmpty())
            return false;
        int ts = c.size();

        long stamp = lock.writeLock();
        try {
            E[] elementData = elements;
            int s = size, newSize = s + ts;
            if (ts > elementData.length - s)
                elementData = allocateNextArray(elementData, newSize);
            int i = s;
            for (E e : c)
                elementData[i++] = e;
            SIZE.setRelease(this, newSize);
        } finally {
            lock.unlockWrite(stamp);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty())
            return false;
        int ts = c.size();

        long stamp = lock.writeLock();
        try {
            E[] elementData = elements;
            int s = size, newSize = s + ts;

            if (ts > elementData.length - s)
                elementData = allocateNextArray(elementData, newSize);

            int numMoved = s - index;
            if (numMoved > 0)
                System.arraycopy(elementData, index,
                        elementData, index + ts,
                        numMoved);
            int i = index;
            for (E e : c)
                elementData[i++] = e;
            SIZE.setRelease(this, newSize);
        } finally {
            lock.unlockWrite(stamp);
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);

        long stamp = lock.writeLock();
        try {
            return batchRemove(c, false, 0, size);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);

        long stamp = lock.writeLock();
        try {
            return batchRemove(c, true, 0, size);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private boolean batchRemove(Collection<?> c, boolean complement,
                                int from, int end) {
        Objects.requireNonNull(c);

        assert lock.isWriteLocked();

        final E[] es = elements;
        int r;
        // Optimize for initial run of survivors
        for (r = from;; r++) {
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
        int sz = size, newSize = sz - (hi - lo);
        System.arraycopy(es, hi, es, lo, sz - hi);

        for (int i = newSize; i < sz; i++)
            es[i] = null;
        SIZE.setRelease(this, newSize);
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
        return new ListItr<>(this, index);
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

            StampedLock lock = list.lock;
            final long stamp = lock.readLock();
            try {
                final E[] es = list.elements;

                next = i >= list.size ? null : es[i];
                prev = p < 0          ? null : es[p];
            } finally {
                lock.unlockRead(stamp);
            }
        }
    }
    private static final VarHandle SIZE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SIZE = l.findVarHandle(ConcurrentArrayList.class, "size", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
