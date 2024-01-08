package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.lang.Integer.numberOfLeadingZeros;

@SuppressWarnings("unchecked")
public class BitwiseSegmentMemory<E extends Number>
        implements BitwiseModifiableMemory<E>, RandomAccess {

    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(Area[].class);
    private static final VarHandle CTL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CTL = l.findVarHandle(BitwiseSegmentMemory.class,
                    "ctl", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Area<E>[] areas;
    private final Function<Integer, Area<E>> mapping;
    private volatile int ctl = 2;

    public BitwiseSegmentMemory(Class<E> componentType) {
        Function<Integer, Area<E>> map;
        if (componentType == byte.class)
            map = len -> (Area<E>) new AreaBytes(new byte[len]);
        else if (componentType == short.class)
            map = len -> (Area<E>) new AreaShorts(new short[len]);
        else if (componentType == int.class)
            map = len -> (Area<E>) new AreaInts(new int[len]);
        else if (componentType == long.class)
            map = len -> (Area<E>) new AreaLongs(new long[len]);
        else
            throw new IllegalArgumentException("Component type is not bitwise");
        Area<E>[] areas = new Area[30];
        areas[0] = map.apply(2);
        this.areas = areas;
        this.mapping = map;
    }

    private int indexForArea(final Area<E> area, final int index) {
        return index < 2 ? index : index - area.length();
    }
    private static int areaForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }

    @Override
    public void realloc(int size) {
        size = Math.max(2, size);
        int n = -1 >>> numberOfLeadingZeros(size - 1);
        if (n < 0 || n >= MAXIMUM_CAPACITY)
            throw new OutOfMemoryError("Required array size too large");
        int c; ++n;
        while ((c = ctl) != n) {
            if (c > n) {
                int index = areaForIndex(c - 1);
                Area<E> area = areas[index];
                if (area != null &&
                        CTL.weakCompareAndSet(this, c, c >> 1))
                    // casSegmentAt(r, segment, null);
                    freeSegment(index);
            } else {
                int index = areaForIndex(c);
                var h = mapping.apply(1 << index);
                if (casSegmentAt(index, null, h)) {
                    int k = (int) CTL.compareAndExchange(this, c, c << 1);
                    if (k < c)
                        casSegmentAt(index, h, null);
                }
            }
        }
    }

    @Override
    public E fetch(int index) {
        return compute(index, (i,area) -> area.arrayAt(i));
    }

    @Override
    public void store(int index, E value) {
        compute(index, (i,area) -> {
            area.setAt(i, value);
            return value;
        });
    }

    @Override
    public E compareAndExchange(int index, E expected, E value) {
        return compute(index, (i,area) -> area.cae(i, expected, value));
    }

    @Override
    public E fetchAndStore(int index, E value) {
        return compute(index, (i,area) -> area.getAndSet(i, value));
    }

    @Override
    public E fetchAndAdd(int index, E value) {
        return compute(index, (i,area) -> area.getAndAdd(i, value));
    }

    @Override
    public E fetchAndBitwiseOr(int index, E mask) {
        return compute(index, (i,area) -> area.getAndBitwiseOr(i, mask));
    }

    @Override
    public E fetchAndBitwiseAnd(int index, E mask) {
        return compute(index, (i,area) -> area.getAndBitwiseAnd(i, mask));
    }

    @Override
    public E fetchAndBitwiseXor(int index, E mask) {
        return compute(index, (i,area) -> area.getAndBitwiseXor(i, mask));
    }

    @Override
    public int length() {
        return (int) CTL.getAcquire(this);
    }


    private E compute(int index,
            BiFunction<Integer, Area<E>, E> consumer) {
        Objects.checkIndex(index, length());

        int exponent = areaForIndex(index);
        Area<E> area = areas[exponent];

        int i = indexForArea(area, index);
        return consumer.apply(i, area);
    }
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("\n");
        for (Area<E> area : areas) {
            if (area == null) break;
            joiner.add(area.toString());
        }
        return joiner.toString();
    }


    private void freeSegment(int i) {
        AA.setRelease(areas, i, null);
    }

    private boolean
    casSegmentAt(int i, Area<E> expected, Area<E> area) {
        return AA.compareAndSet(areas, i, expected, area);
    }

    private interface Area<E extends Number> {
        int length();
        E arrayAt(int index);

        void setAt(int index, E value);

        E getAndSet(int index, E value);

        E cae(int i, E expected, E value);

        E getAndAdd(int i, E value);

        E getAndBitwiseOr(int index, E mask);
        E getAndBitwiseAnd(int index, E mask);
        E getAndBitwiseXor(int index, E mask);
    }

    private record AreaLongs(long[] array) implements Area<Long> {
        private static final VarHandle
                LONGS = MethodHandles.arrayElementVarHandle(long[].class);

        @Override public int length()
        { return array.length; }

        @Override public Long arrayAt(int index)
        { return (long) LONGS.getAcquire(array, index); }

        @Override public void setAt(int index, Long value)
        { LONGS.setRelease(array, index, value); }

        @Override public Long getAndSet(int index, Long value)
        { return (long) LONGS.getAndSet(array, index, value); }

        @Override public Long cae(int i, Long expected, Long value)
        { return (long) LONGS.compareAndExchange(array, i, expected, value); }

        @Override public Long getAndAdd(int i, Long value)
        { return (long) LONGS.getAndAdd(array, i, value); }

        @Override public Long getAndBitwiseOr(int index, Long mask)
        { return (long) LONGS.getAndBitwiseOr(array, index, mask); }

        @Override public Long getAndBitwiseAnd(int index, Long mask)
        { return (long) LONGS.getAndBitwiseAnd(array, index, mask); }

        @Override public Long getAndBitwiseXor(int index, Long mask)
        { return (long) LONGS.getAndBitwiseXor(array, index, mask); }
    }
    private record AreaInts(int[] array) implements Area<Integer> {
        private static final VarHandle
                INTEGERS = MethodHandles.arrayElementVarHandle(int[].class);

        @Override public int length()
        { return array.length; }

        @Override public Integer arrayAt(int index)
        { return (int) INTEGERS.getAcquire(array, index); }

        @Override public void setAt(int index, Integer value)
        { INTEGERS.setRelease(array, index, value); }

        @Override public Integer getAndSet(int index, Integer value)
        { return (int) INTEGERS.getAndSet(array, index, value); }

        @Override public Integer cae(int i, Integer expected, Integer value)
        { return (int) INTEGERS.compareAndExchange(array, i, expected, value); }

        @Override public Integer getAndAdd(int i, Integer value)
        { return (int) INTEGERS.getAndAdd(array, i, value); }

        @Override public Integer getAndBitwiseOr(int index, Integer mask)
        { return (int) INTEGERS.getAndBitwiseOr(array, index, mask); }

        @Override public Integer getAndBitwiseAnd(int index, Integer mask)
        { return (int) INTEGERS.getAndBitwiseAnd(array, index, mask); }

        @Override public Integer getAndBitwiseXor(int index, Integer mask)
        { return (int) INTEGERS.getAndBitwiseXor(array, index, mask); }
    }
    private record AreaShorts(short[] array) implements Area<Short> {
        private static final VarHandle
                SHORTS = MethodHandles.arrayElementVarHandle(short[].class);

        @Override public int length()
        { return array.length; }

        @Override public Short arrayAt(int index)
        { return (short) SHORTS.getAcquire(array, index); }

        @Override public void setAt(int index, Short value)
        { SHORTS.setRelease(array, index, value); }

        @Override public Short getAndSet(int index, Short value)
        { return (short) SHORTS.getAndSet(array, index, value); }

        @Override public Short cae(int i, Short expected, Short value)
        { return (short) SHORTS.compareAndExchange(array, i, expected, value); }

        @Override public Short getAndAdd(int i, Short value)
        { return (short) SHORTS.getAndAdd(array, i, value); }

        @Override public Short getAndBitwiseOr(int index, Short mask)
        { return (short) SHORTS.getAndBitwiseOr(array, index, mask); }

        @Override public Short getAndBitwiseAnd(int index, Short mask)
        { return (short) SHORTS.getAndBitwiseAnd(array, index, mask); }

        @Override public Short getAndBitwiseXor(int index, Short mask)
        { return (short) SHORTS.getAndBitwiseXor(array, index, mask); }
    }
    private record AreaBytes(byte[] array) implements Area<Byte> {
        private static final VarHandle
                BYTES = MethodHandles.arrayElementVarHandle(byte[].class);

        @Override public int length()
        { return array.length; }

        @Override public Byte arrayAt(int index)
        { return (byte) BYTES.getAcquire(array, index); }

        @Override public void setAt(int index, Byte value)
        { BYTES.setRelease(array, index, value); }

        @Override public Byte getAndSet(int index, Byte value)
        { return (byte) BYTES.getAndSet(array, index, value); }

        @Override public Byte cae(int i, Byte expected, Byte value)
        { return (byte) BYTES.compareAndExchange(array, i, expected, value); }

        @Override public Byte getAndAdd(int i, Byte value)
        { return (byte) BYTES.getAndAdd(array, i, value); }

        @Override public Byte getAndBitwiseOr(int index, Byte mask)
        { return (byte) BYTES.getAndBitwiseOr(array, index, mask); }

        @Override public Byte getAndBitwiseAnd(int index, Byte mask)
        { return (byte) BYTES.getAndBitwiseAnd(array, index, mask); }

        @Override public Byte getAndBitwiseXor(int index, Byte mask)
        { return (byte) BYTES.getAndBitwiseXor(array, index, mask); }
    }
}
