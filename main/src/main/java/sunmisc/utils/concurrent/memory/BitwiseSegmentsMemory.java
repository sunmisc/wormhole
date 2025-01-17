package sunmisc.utils.concurrent.memory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.lang.Integer.numberOfLeadingZeros;

public final class BitwiseSegmentsMemory<E extends Number>
        implements BitwiseModifiableMemory<E> {

    private final BitwiseModifiableMemory<E>[] areas;
    private final Function<Integer, Area<E>> mapped;

    private BitwiseSegmentsMemory(final BitwiseModifiableMemory<E>[] areas,
                                  final Function<Integer, Area<E>> mapped) {
        this.areas = areas;
        this.mapped = mapped;
    }

    public BitwiseSegmentsMemory(final Class<E> componentType, final int size) {
        final Function<Integer, Area<E>> map = typeToArea(componentType);
        final int segments = 32 - numberOfLeadingZeros(Math.max(size - 1, 1));
        @SuppressWarnings("unchecked")
        final BitwiseModifiableMemory<E>[] areas = new BitwiseModifiableMemory[segments];
        areas[0] = map.apply(2);
        for (int segment = 1; segment < segments; ++segment) {
            areas[segment] = map.apply(1 << segment);
        }
        this.mapped = map;
        this.areas = areas;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Number> Function<Integer, Area<E>> typeToArea(final Class<E> type) {
        final Function<Integer, Area<E>> map;
        if (type == byte.class) {
            map = len -> (Area<E>) new AreaBytes(new byte[len]);
        } else if (type == short.class) {
            map = len -> (Area<E>) new AreaShorts(new short[len]);
        } else if (type == int.class) {
            map = len -> (Area<E>) new AreaInts(new int[len]);
        } else if (type == long.class) {
            map = len -> (Area<E>) new AreaLongs(new long[len]);
        } else {
            throw new IllegalArgumentException("Component type is not bitwise");
        }
        return map;
    }

    private static int areaForIndex(final int index) {
        return index < 2 ? 0 : 31 - numberOfLeadingZeros(index);
    }

    private int indexForArea(final BitwiseModifiableMemory<E> area, final int index) {
        return index < 2 ? index : index - area.length();
    }

    @Override
    public BitwiseModifiableMemory<E> realloc(final int size) {
        final int aligned = 32 - numberOfLeadingZeros(Math.max(size - 1, 1));
        final BitwiseModifiableMemory<E>[] prev = this.areas;
        final BitwiseModifiableMemory<E>[] copy = Arrays.copyOf(prev, aligned);
        for (int p = prev.length; p < aligned; ++p) {
            copy[p] = this.mapped.apply(1 << p);
        }
        return new BitwiseSegmentsMemory<>(copy, this.mapped);
    }

    @Override
    public E fetch(final int index) {
        return this.compute(index, (i, area) -> area.fetch(i));
    }

    @Override
    public void store(final int index, final E value) {
        this.compute(index, (i, area) -> {
            area.store(i, value);
            return value;
        });
    }

    @Override
    public E compareAndExchange(final int index, final E expected, final E value) {
        return this.compute(index, (i, area) -> area.compareAndExchange(i, expected, value));
    }

    @Override
    public E fetchAndStore(final int index, final E value) {
        return this.compute(index, (i, area) -> area.fetchAndStore(i, value));
    }

    @Override
    public E fetchAndAdd(final int index, final E value) {
        return this.compute(index, (i, area) -> area.fetchAndAdd(i, value));
    }

    @Override
    public E fetchAndBitwiseOr(final int index, final E mask) {
        return this.compute(index, (i, area) -> area.fetchAndBitwiseOr(i, mask));
    }

    @Override
    public E fetchAndBitwiseAnd(final int index, final E mask) {
        return this.compute(index, (i, area) -> area.fetchAndBitwiseAnd(i, mask));
    }

    @Override
    public E fetchAndBitwiseXor(final int index, final E mask) {
        return this.compute(index, (i, area) -> area.fetchAndBitwiseXor(i, mask));
    }

    @Override
    public int length() {
        return 1 << this.areas.length;
    }

    private E compute(final int index,
                      final BiFunction<Integer, BitwiseModifiableMemory<E>, E> consumer) {
        final int exponent = areaForIndex(index);
        final BitwiseModifiableMemory<E> area = this.areas[exponent];
        final int i = this.indexForArea(area, index);
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

    private interface Area<E extends Number>
            extends BitwiseModifiableMemory<E> {
        @Override
        default BitwiseModifiableMemory<E> realloc(final int size) throws OutOfMemoryError {
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
