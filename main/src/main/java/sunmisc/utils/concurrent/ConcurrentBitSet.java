package sunmisc.utils.concurrent;

import sunmisc.utils.concurrent.memory.BitwiseModifiableMemory;
import sunmisc.utils.concurrent.memory.BitwiseSegmentMemory;

import java.util.Objects;

public class ConcurrentBitSet {
    private static final int ADDRESS_BITS_PER_CELL
            = Integer.numberOfTrailingZeros(Long.SIZE);
    private static final int BITS_PER_CELL =
            1 << ADDRESS_BITS_PER_CELL;

    /* Used to shift left or right for a partial word mask */
    private static final long CELL_MASK = -1;
    private final BitwiseModifiableMemory<Long> memory
            = new BitwiseSegmentMemory<>(long.class);

    public ConcurrentBitSet() { }

    public ConcurrentBitSet(int bits) {
        memory.realloc(bits);
    }

    public boolean get(int bitIndex) {
        Objects.checkIndex(bitIndex, memory.length());
        final int index = cellIndex(bitIndex);
        return (memory.get(index) & (1L << bitIndex)) != 0;
    }

    private void ensureCellFor(int index) {
        int wordsRequired = index+1;
        if (memory.length() < wordsRequired)
            memory.realloc(index);
    }

    public void set(int bitIndex) {
        int n = memory.length();
        Objects.checkIndex(bitIndex, n);
        final int index = cellIndex(bitIndex);
        int wordsRequired = index+1;
        if (n < wordsRequired)
            memory.realloc(index);
        memory.getAndBitwiseOr(index, (1L << bitIndex)); // Restores invariants
    }

    private static int cellIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_CELL;
    }
}