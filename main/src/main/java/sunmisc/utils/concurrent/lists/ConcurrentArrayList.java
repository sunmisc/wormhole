package sunmisc.utils.concurrent.lists;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

/**
 * ConcurrentArrayList on the knee
 * <p>This implementation is not famous for "abstruse" solutions
 * Maybe I'll come up with something better when I'm in the mood
 * <p>But still this implementation can be better than COW
 * <p>and even better than Collections.synchronizedList(new ArrayList<>());
 * <p>Instead of a regular lock, I used StampedLock
 * <p>
 * to improve reading performance
 * <p>it is much better than blocking (actually makes one volatile-read)
 * <p>in a low competition environment
 *
 * @author Sunmisc Unsafe
 * @param <E> the type of elements in this list
 */
public class ConcurrentArrayList<E>
        extends AbstractSequentialList<E>
        implements List<E>, RandomAccess {
    private final StampedLock lock
            = new StampedLock();
    private final AtomicInteger indexation
            = new AtomicInteger();
    private int size;
    private volatile E[] array;


    @Override
    public E get(int index) {

        long stamp = lock.tryOptimisticRead();
        try {
            for (;; stamp = lock.readLock()) {
                if (stamp == 0L)
                    continue;
                Objects.checkIndex(index,
                        (int) SIZE.getVolatile(this));
                E element = array[index];
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

        for (;;) {
            int i = indexation.get();
            E[] arr = array;

            boolean exp = i >= arr.length;

            long stamp = exp ? lock.writeLock() : lock.readLock();

            try {
                arr = array;

                i = indexation.getAndAdd(1);
                int q = i + 1;

                if (exp && i >= arr.length) {
                    arr = expand(arr, q);
                    arr[i] = e;
                    array = arr;
                    break;
                } else if (i < arr.length) {
                    AA.setRelease(arr, i, e);
                    break;
                }
            } finally {
                lock.unlock(stamp);
            }
        }
        SIZE.getAndAddRelease(this, 1);
        return true;
    }
    private E[] expand(E[] oldArray, int minCapacity) {
        int oldCapacity = oldArray.length;
        int cap = Math.max(minCapacity, oldCapacity + (oldCapacity >>> 1));

        return Arrays.copyOf(oldArray, cap);
    }


    @Override
    public E set(int index, E element) {
        long stamp = lock.writeLock();
        try {
            Objects.checkIndex(index, size);
            E oldValue = array[index];
            array[index] = element;

            return oldValue;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(int index, E element) {
        long stamp = lock.writeLock();
        try {
            E[] es = array;
            int s = size, u = s + 1;
            if (s == es.length)
                es = expand(es, u);
            System.arraycopy(es, index,
                    es, index + 1,
                    s - index);
            es[index] = element;

            if (s == es.length)
                array = es;
            SIZE.setRelease(this, u);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public E remove(int index) {
        long stamp = lock.writeLock();
        try {
            return fastRemove(index);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    private E fastRemove(int index) {
        E[] es = array; E oldVal = es[index];
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
            int i = indexOf(o);
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
            array = (E[]) new Object[16];
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
        Object[] es = array;
        for (int i = start; i < end; i++) {
            if (Objects.equals(es[i], o))
                return i;
        }
        return -1;
    }
    int lastIndexOfRange(Object o, int start, int end) {
        Object[] es = array;
        for (int i = end - 1; i >= start; i--) {
            if (Objects.equals(es[i], o))
                return i;
        }
        return -1;
    }
    @NotNull
    @Override
    public Iterator<E> iterator() {
        return null;
    }
    @NotNull
    @Override
    public ListIterator<E> listIterator() {
        return null;
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator(int index) {
        return null;
    }

    @NotNull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return null;
    }
    @Override
    public Object[] toArray() {
        long stamp = lock.readLock();
        try {
            return Arrays.copyOf(array, size);
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
                return (T[]) Arrays.copyOf(array, size, a.getClass());
            System.arraycopy(array, 0, a, 0, size);
            if (a.length > size)
                a[size] = null;
            return a;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
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
    public boolean addAll(@NotNull Collection<? extends E> c) {
        if (c.isEmpty()) return false;
        int ts = c.size();

        long stamp = lock.writeLock();
        try {
            E[] elementData = array;
            int s = size, newSize = s + ts;
            if (ts > elementData.length - s)
                elementData = expand(elementData, newSize);
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
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
        if (c.isEmpty()) return false;
        int ts = c.size();

        long stamp = lock.writeLock();
        try {
            E[] elementData = array;
            int s = size, newSize = s + ts;

            if (ts > elementData.length - s)
                elementData = expand(elementData, newSize);

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
    public boolean removeAll(@NotNull Collection<?> c) {
        long stamp = lock.writeLock();
        try {
            return batchRemove(c, false, 0, size);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
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

        final E[] es = array;
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

    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Object[].class);
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
