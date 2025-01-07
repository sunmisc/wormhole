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
            final MethodHandles.Lookup l = MethodHandles.lookup();
            CTL = l.findVarHandle(BitwiseSegmentMemory.class,
                    "ctl", int.class);
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final BitwiseModifiableMemory<E>[] areas;
    private final Function<Integer, Area<E>> mapping;
    private volatile int ctl = 2;

    public BitwiseSegmentMemory(final Class<E> componentType) {
        final Function<Integer, Area<E>> map;
        if (componentType == byte.class) {
            map = len -> (Area<E>) new AreaBytes(new byte[len]);
        } else if (componentType == short.class) {
            map = len -> (Area<E>) new AreaShorts(new short[len]);
        } else if (componentType == int.class) {
            map = len -> (Area<E>) new AreaInts(new int[len]);
        } else if (componentType == long.class) {
            map = len -> (Area<E>) new AreaLongs(new long[len]);
        } else {
            throw new IllegalArgumentException("Component type is not bitwise");
        }
        final BitwiseModifiableMemory<E>[] areas = new BitwiseModifiableMemory[30];
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
        if (n < 0 || n >= MAXIMUM_CAPACITY) {
            throw new OutOfMemoryError("Required array size too large");
        }
        int c; ++n;
        while ((c = this.ctl) != n) {
            if (c > n) {
                final int index = areaForIndex(c - 1);
                final BitwiseModifiableMemory<E> area = this.areas[index];
                if (area != null &&
                        CTL.weakCompareAndSet(this, c, c >> 1))
                    // casSegmentAt(r, segment, null);
                {
                    freeSegment(index);
                }
            } else {
                final int index = areaForIndex(c);
                final var h = this.mapping.apply(1 << index);
                if (casSegmentAt(index, null, h)) {
                    final int k = (int) CTL.compareAndExchange(this, c, c << 1);
                    if (k < c) {
                        casSegmentAt(index, h, null);
                    }
                }
            }
        }
        return this;
    }

    @Override
    public E fetch(final int index) {
        return compute(index, (i,area) -> area.fetch(i));
    }

    @Override
    public void store(final int index, final E value) {
        compute(index, (i,area) -> {
            area.store(i, value);
            return value;
        });
    }

    @Override
    public E compareAndExchange(final int index, final E expected, final E value) {
        return compute(index, (i, area) -> area.compareAndExchange(i, expected, value));
    }

    @Override
    public E fetchAndStore(final int index, final E value) {
        return compute(index, (i, area) -> area.fetchAndStore(i, value));
    }

    @Override
    public E fetchAndAdd(final int index, final E value) {
        return compute(index, (i,area) -> area.fetchAndAdd(i, value));
    }

    @Override
    public E fetchAndBitwiseOr(final int index, final E mask) {
        return compute(index, (i,area) -> area.fetchAndBitwiseOr(i, mask));
    }

    @Override
    public E fetchAndBitwiseAnd(final int index, final E mask) {
        return compute(index, (i,area) -> area.fetchAndBitwiseAnd(i, mask));
    }

    @Override
    public E fetchAndBitwiseXor(final int index, final E mask) {
        return compute(index, (i,area) -> area.fetchAndBitwiseXor(i, mask));
    }

    @Override
    public int length() {
        return (int) CTL.getAcquire(this);
    }

    private E compute(final int index,
                      final BiFunction<Integer, BitwiseModifiableMemory<E>, E> consumer) {
        Objects.checkIndex(index, length());

        final int exponent = areaForIndex(index);
        final BitwiseModifiableMemory<E> area = this.areas[exponent];

        final int i = indexForArea(area, index);
        return consumer.apply(i, area);
    }
    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner("\n");
        for (final BitwiseModifiableMemory<E> area : this.areas) {
            if (area == null) {
                break;
            }
            joiner.add(area.toString());
        }
        return joiner.toString();
    }


    private void freeSegment(final int i) {
        AA.setRelease(this.areas, i, null);
    }

    private boolean
    casSegmentAt(final int i, final Area<E> expected, final Area<E> area) {
        return AA.compareAndSet(this.areas, i, expected, area);
    }


    private interface Area<E extends Number>
            extends BitwiseModifiableMemory<E> {
        @Override
        default ModifiableMemory<E> realloc(final int size) throws OutOfMemoryError {
            throw new UnsupportedOperationException();
        }
    }

    private record AreaLongs(long[] array) implements Area<Long> {
        private static final VarHandle
                LONGS = MethodHandles.arrayElementVarHandle(long[].class);

        @Override public int length()
        { return this.array.length; }

        @Override public Long fetch(final int index)
        { return (long) LONGS.getAcquire(this.array, index); }

        @Override public void store(final int index, final Long value)
        { LONGS.setRelease(this.array, index, value); }

        @Override public Long fetchAndStore(final int index, final Long value)
        { return (long) LONGS.getAndSet(this.array, index, value); }

        @Override public Long compareAndExchange(final int i, final Long expected, final Long value)
        { return (long) LONGS.compareAndExchange(this.array, i, expected, value); }

        @Override public Long fetchAndAdd(final int i, final Long value)
        { return (long) LONGS.getAndAdd(this.array, i, value); }

        @Override public Long fetchAndBitwiseOr(final int index, final Long mask)
        { return (long) LONGS.getAndBitwiseOr(this.array, index, mask); }

        @Override public Long fetchAndBitwiseAnd(final int index, final Long mask)
        { return (long) LONGS.getAndBitwiseAnd(this.array, index, mask); }

        @Override public Long fetchAndBitwiseXor(final int index, final Long mask)
        { return (long) LONGS.getAndBitwiseXor(this.array, index, mask); }
    }
    private record AreaInts(int[] array) implements Area<Integer> {
        private static final VarHandle
                INTEGERS = MethodHandles.arrayElementVarHandle(int[].class);

        @Override public int length()
        { return this.array.length; }

        @Override public Integer fetch(final int index)
        { return (int) INTEGERS.getAcquire(this.array, index); }

        @Override public void store(final int index, final Integer value)
        { INTEGERS.setRelease(this.array, index, value); }

        @Override public Integer fetchAndStore(final int index, final Integer value)
        { return (int) INTEGERS.getAndSet(this.array, index, value); }

        @Override public Integer compareAndExchange(final int i, final Integer expected, final Integer value)
        { return (int) INTEGERS.compareAndExchange(this.array, i, expected, value); }

        @Override public Integer fetchAndAdd(final int i, final Integer value)
        { return (int) INTEGERS.getAndAdd(this.array, i, value); }

        @Override public Integer fetchAndBitwiseOr(final int index, final Integer mask)
        { return (int) INTEGERS.getAndBitwiseOr(this.array, index, mask); }

        @Override public Integer fetchAndBitwiseAnd(final int index, final Integer mask)
        { return (int) INTEGERS.getAndBitwiseAnd(this.array, index, mask); }

        @Override public Integer fetchAndBitwiseXor(final int index, final Integer mask)
        { return (int) INTEGERS.getAndBitwiseXor(this.array, index, mask); }
    }
    private record AreaShorts(short[] array) implements Area<Short> {
        private static final VarHandle
                SHORTS = MethodHandles.arrayElementVarHandle(short[].class);

        @Override public int length()
        { return this.array.length; }

        @Override public Short fetch(final int index)
        { return (short) SHORTS.getAcquire(this.array, index); }

        @Override public void store(final int index, final Short value)
        { SHORTS.setRelease(this.array, index, value); }

        @Override public Short fetchAndStore(final int index, final Short value)
        { return (short) SHORTS.getAndSet(this.array, index, value); }

        @Override public Short compareAndExchange(final int i, final Short expected, final Short value)
        { return (short) SHORTS.compareAndExchange(this.array, i, expected, value); }

        @Override public Short fetchAndAdd(final int i, final Short value)
        { return (short) SHORTS.getAndAdd(this.array, i, value); }

        @Override public Short fetchAndBitwiseOr(final int index, final Short mask)
        { return (short) SHORTS.getAndBitwiseOr(this.array, index, mask); }

        @Override public Short fetchAndBitwiseAnd(final int index, final Short mask)
        { return (short) SHORTS.getAndBitwiseAnd(this.array, index, mask); }

        @Override public Short fetchAndBitwiseXor(final int index, final Short mask)
        { return (short) SHORTS.getAndBitwiseXor(this.array, index, mask); }
    }
    private record AreaBytes(byte[] array) implements Area<Byte> {
        private static final VarHandle
                BYTES = MethodHandles.arrayElementVarHandle(byte[].class);

        @Override public int length()
        { return this.array.length; }

        @Override public Byte fetch(final int index)
        { return (byte) BYTES.getAcquire(this.array, index); }

        @Override public void store(final int index, final Byte value)
        { BYTES.setRelease(this.array, index, value); }

        @Override public Byte fetchAndStore(final int index, final Byte value)
        { return (byte) BYTES.getAndSet(this.array, index, value); }

        @Override public Byte compareAndExchange(final int i, final Byte expected, final Byte value)
        { return (byte) BYTES.compareAndExchange(this.array, i, expected, value); }

        @Override public Byte fetchAndAdd(final int i, final Byte value)
        { return (byte) BYTES.getAndAdd(this.array, i, value); }

        @Override public Byte fetchAndBitwiseOr(final int index, final Byte mask)
        { return (byte) BYTES.getAndBitwiseOr(this.array, index, mask); }

        @Override public Byte fetchAndBitwiseAnd(final int index, final Byte mask)
        { return (byte) BYTES.getAndBitwiseAnd(this.array, index, mask); }

        @Override public Byte fetchAndBitwiseXor(final int index, final Byte mask)
        { return (byte) BYTES.getAndBitwiseXor(this.array, index, mask); }
    }
}
