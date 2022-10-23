package zelva.utils.concurrent;

import zelva.annotation.PreviewFeature;

import java.util.Objects;
import java.util.function.UnaryOperator;

@PreviewFeature
public class ConcurrentBitSet {
    private static final int ADDRESS_BITS_PER_WORD
            = Integer.numberOfTrailingZeros(Long.SIZE);

    /* Used to shift left or right for a partial word mask */
    private static final long WORD_MASK = 0xFFFFFFFFFFFFFFFFL; // -1

    private final ConcurrentTransferArrayMap<Long> words
            = new ConcurrentTransferArrayMap<>(1);

    public void set(int bitIndex) {
        final int wordIndex = wordIndex(bitIndex);

        if (wordIndex >= words.size()) {
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

    public int nextSetBit(int start) {
        int u = wordIndex(start);
        if (u >= words.size()) return -1;
        Long w = words.get(u);
        if (w == null)
            return -1;
        for (Long bits = w & (WORD_MASK << start);;) {
            if (bits != 0)
                return (u * Long.SIZE) + Long.numberOfTrailingZeros(bits);
            else if (++u >= words.size() ||
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

    private Long updateAndGet(int index, UnaryOperator<Long> operator) {
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
