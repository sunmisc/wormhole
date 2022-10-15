package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class ConcurrentBitSet {
    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

    /* Used to shift left or right for a partial word mask */
    private static final long WORD_MASK = 0xFFFFFFFFFFFFFFFFL; // -1


    final BitSet set = new BitSet();
    final ConcurrentArrayCells<Long> words
            = new ConcurrentArrayCells<>(1);

    public static void main(String[] args) {
        ConcurrentBitSet bitSet = new ConcurrentBitSet();
        BitSet bitSet1 = new BitSet();

        for (int i = 0; i < 10; ++i) {
            bitSet.set(i);
            bitSet1.set(i);
        }

        bitSet.removeNextSetBit(0);
        bitSet1.clear(0);
        System.out.println(bitSet);
        System.out.println(bitSet1);
    }

    public void set(int bitIndex) {
        final int wordIndex = wordIndex(bitIndex);

        if (wordIndex >= words.length()) {
            words.resize(x -> wordIndex >= x ? x << 1 : x);
        }

        long mask = (1L << bitIndex);
        getAndBitwiseOr(wordIndex, mask);
    }

    public void clear(int bitIndex) {
        int wordIndex = wordIndex(bitIndex);

        long mask = ~(1L << bitIndex);

        updateAndGet(wordIndex, x -> x == null ? null : x & mask);
    }

    public int removeNextSetBit(int start) {
        int u = wordIndex(start);

        Long w = words.get(u);
        if (w == null)
            return -1;
        Long bits = w;
        for (Long i = w & (WORD_MASK << start);;) {
            if (bits != 0) {
                int idx = (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(i);

                if (Objects.equals(words.cae(u,
                        bits,
                        bits & ~(1L << idx)
                ), bits)) { return idx; }

            } else if (++u >= words.length() ||
                    (i = bits = words.get(u)) == null) {
                return -1;
            }
        }
    }

    public int nextSetBit(int start) {
        int u = wordIndex(start);
        if (u >= words.length()) return -1;
        Long w = words.get(u);
        if (w == null)
            return -1;
        for (Long bits = w & (WORD_MASK << start);;) {
            if (bits != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(bits);
            else if (++u >= words.length() ||
                    (bits = words.get(u)) == null) {
                return -1;
            }
        }
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (int i = 0;;) {
            int t = nextSetBit(i);
            if (t < 0) break;
            builder.append(t).append(' ');
            i = t + 1;
        }
        return builder.toString();
    }

    private long updateAndGet(int index, UnaryOperator<Long> operator) {
        for (Long n, t;;) {
            t = operator.apply(n = words.get(index));
            if (Objects.equals(n, t)) {
                return t;
            } else if (Objects.equals(words.cae(index, n, t), t)) {
                return n;
            }
        }
    }
    private long getAndBitwiseOr(int index, long mask) {
        return updateAndGet(index, x -> x == null ? mask : x | mask);
    }


    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

}
