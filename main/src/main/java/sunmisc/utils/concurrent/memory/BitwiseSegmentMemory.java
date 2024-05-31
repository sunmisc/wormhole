package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.lang.Integer.numberOfLeadingZeros;

@SuppressWarnings("unchecked")
public final class BitwiseSegmentMemory<E extends Number>
        implements BitwiseModifiableMemory<E> {

    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final VarHandle AA
            = MethodHandles.arrayElementVarHandle(BitwiseModifiableMemory[].class);
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

    private final BitwiseModifiableMemory<E>[] areas;
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
        BitwiseModifiableMemory<E>[] areas = new BitwiseModifiableMemory[30];
        areas[0] = map.apply(2);
        this.areas = areas;
        this.mapping = map;
    }

    private int indexForArea(final BitwiseModifiableMemory<E> area, final int index) {
        return index < 2 ? index : index - area.length();
    }
    private static int areaForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }

    @Override
    public ModifiableMemory<E> realloc(int size) {
        size = Math.max(2, size);
        int n = -1 >>> numberOfLeadingZeros(size - 1);
        if (n < 0 || n >= MAXIMUM_CAPACITY)
            throw new OutOfMemoryError("Required array size too large");
        int c; ++n;
        while ((c = ctl) != n) {
            if (c > n) {
                int index = areaForIndex(c - 1);
                BitwiseModifiableMemory<E> area = areas[index];
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
        return this;
    }

    @Override
    public E fetch(int index) {
        return compute(index, (i,area) -> area.fetch(i));
    }

    @Override
    public void store(int index, E value) {
        compute(index, (i,area) -> {
            area.store(i, value);
            return value;
        });
    }

    @Override
    public E compareAndExchange(int index, E expected, E value) {
        return compute(index, (i, area) -> area.compareAndExchange(i, expected, value));
    }

    @Override
    public E fetchAndStore(int index, E value) {
        return compute(index, (i, area) -> area.fetchAndStore(i, value));
    }

    @Override
    public E fetchAndAdd(int index, E value) {
        return compute(index, (i,area) -> area.fetchAndAdd(i, value));
    }

    @Override
    public E fetchAndBitwiseOr(int index, E mask) {
        return compute(index, (i,area) -> area.fetchAndBitwiseOr(i, mask));
    }

    @Override
    public E fetchAndBitwiseAnd(int index, E mask) {
        return compute(index, (i,area) -> area.fetchAndBitwiseAnd(i, mask));
    }

    @Override
    public E fetchAndBitwiseXor(int index, E mask) {
        return compute(index, (i,area) -> area.fetchAndBitwiseXor(i, mask));
    }

    @Override
    public int length() {
        return (int) CTL.getAcquire(this);
    }

    private E compute(int index,
            BiFunction<Integer, BitwiseModifiableMemory<E>, E> consumer) {
        Objects.checkIndex(index, length());

        int exponent = areaForIndex(index);
        BitwiseModifiableMemory<E> area = areas[exponent];

        int i = indexForArea(area, index);
        return consumer.apply(i, area);
    }
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("\n");
        for (BitwiseModifiableMemory<E> area : areas) {
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


    private interface Area<E extends Number>
            extends BitwiseModifiableMemory<E> {
        @Override
        default ModifiableMemory<E> realloc(int size) throws OutOfMemoryError {
            throw new UnsupportedOperationException();
        }
    }

    private record AreaLongs(long[] array) implements Area<Long> {
        private static final VarHandle
                LONGS = MethodHandles.arrayElementVarHandle(long[].class);

        @Override public int length()
        { return array.length; }

        @Override public Long fetch(int index)
        { return (long) LONGS.getAcquire(array, index); }

        @Override public void store(int index, Long value)
        { LONGS.setRelease(array, index, value); }

        @Override public Long fetchAndStore(int index, Long value)
        { return (long) LONGS.getAndSet(array, index, value); }

        @Override public Long compareAndExchange(int i, Long expected, Long value)
        { return (long) LONGS.compareAndExchange(array, i, expected, value); }

        @Override public Long fetchAndAdd(int i, Long value)
        { return (long) LONGS.getAndAdd(array, i, value); }

        @Override public Long fetchAndBitwiseOr(int index, Long mask)
        { return (long) LONGS.getAndBitwiseOr(array, index, mask); }

        @Override public Long fetchAndBitwiseAnd(int index, Long mask)
        { return (long) LONGS.getAndBitwiseAnd(array, index, mask); }

        @Override public Long fetchAndBitwiseXor(int index, Long mask)
        { return (long) LONGS.getAndBitwiseXor(array, index, mask); }
    }
    private record AreaInts(int[] array) implements Area<Integer> {
        private static final VarHandle
                INTEGERS = MethodHandles.arrayElementVarHandle(int[].class);

        @Override public int length()
        { return array.length; }

        @Override public Integer fetch(int index)
        { return (int) INTEGERS.getAcquire(array, index); }

        @Override public void store(int index, Integer value)
        { INTEGERS.setRelease(array, index, value); }

        @Override public Integer fetchAndStore(int index, Integer value)
        { return (int) INTEGERS.getAndSet(array, index, value); }

        @Override public Integer compareAndExchange(int i, Integer expected, Integer value)
        { return (int) INTEGERS.compareAndExchange(array, i, expected, value); }

        @Override public Integer fetchAndAdd(int i, Integer value)
        { return (int) INTEGERS.getAndAdd(array, i, value); }

        @Override public Integer fetchAndBitwiseOr(int index, Integer mask)
        { return (int) INTEGERS.getAndBitwiseOr(array, index, mask); }

        @Override public Integer fetchAndBitwiseAnd(int index, Integer mask)
        { return (int) INTEGERS.getAndBitwiseAnd(array, index, mask); }

        @Override public Integer fetchAndBitwiseXor(int index, Integer mask)
        { return (int) INTEGERS.getAndBitwiseXor(array, index, mask); }
    }
    private record AreaShorts(short[] array) implements Area<Short> {
        private static final VarHandle
                SHORTS = MethodHandles.arrayElementVarHandle(short[].class);

        @Override public int length()
        { return array.length; }

        @Override public Short fetch(int index)
        { return (short) SHORTS.getAcquire(array, index); }

        @Override public void store(int index, Short value)
        { SHORTS.setRelease(array, index, value); }

        @Override public Short fetchAndStore(int index, Short value)
        { return (short) SHORTS.getAndSet(array, index, value); }

        @Override public Short compareAndExchange(int i, Short expected, Short value)
        { return (short) SHORTS.compareAndExchange(array, i, expected, value); }

        @Override public Short fetchAndAdd(int i, Short value)
        { return (short) SHORTS.getAndAdd(array, i, value); }

        @Override public Short fetchAndBitwiseOr(int index, Short mask)
        { return (short) SHORTS.getAndBitwiseOr(array, index, mask); }

        @Override public Short fetchAndBitwiseAnd(int index, Short mask)
        { return (short) SHORTS.getAndBitwiseAnd(array, index, mask); }

        @Override public Short fetchAndBitwiseXor(int index, Short mask)
        { return (short) SHORTS.getAndBitwiseXor(array, index, mask); }
    }
    private record AreaBytes(byte[] array) implements Area<Byte> {
        private static final VarHandle
                BYTES = MethodHandles.arrayElementVarHandle(byte[].class);

        @Override public int length()
        { return array.length; }

        @Override public Byte fetch(int index)
        { return (byte) BYTES.getAcquire(array, index); }

        @Override public void store(int index, Byte value)
        { BYTES.setRelease(array, index, value); }

        @Override public Byte fetchAndStore(int index, Byte value)
        { return (byte) BYTES.getAndSet(array, index, value); }

        @Override public Byte compareAndExchange(int i, Byte expected, Byte value)
        { return (byte) BYTES.compareAndExchange(array, i, expected, value); }

        @Override public Byte fetchAndAdd(int i, Byte value)
        { return (byte) BYTES.getAndAdd(array, i, value); }

        @Override public Byte fetchAndBitwiseOr(int index, Byte mask)
        { return (byte) BYTES.getAndBitwiseOr(array, index, mask); }

        @Override public Byte fetchAndBitwiseAnd(int index, Byte mask)
        { return (byte) BYTES.getAndBitwiseAnd(array, index, mask); }

        @Override public Byte fetchAndBitwiseXor(int index, Byte mask)
        { return (byte) BYTES.getAndBitwiseXor(array, index, mask); }
    }
}
