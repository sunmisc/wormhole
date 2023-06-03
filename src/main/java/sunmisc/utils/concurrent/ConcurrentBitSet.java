package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.StringJoiner;


// todo:
class ConcurrentBitSet {
    private static final int ADDRESS_BITS_PER_WORD
            = Integer.numberOfTrailingZeros(Long.SIZE);

    /* Used to shift left or right for a partial word mask */
    private static final long WORD_MASK = 0xFFFFFFFFFFFFFFFFL; // -1

    private final UnblockingArrayBuffer<Long> words
            = new UnblockingArrayBuffer<>(1);

    public void set(int bitIndex) {
        final int wordIndex = wordIndex(bitIndex);

        if (wordIndex >= words.size())
            words.resize(x -> wordIndex >= x ? x << 1 : x);

        long mask = (1L << bitIndex);

        words.merge(wordIndex, mask, (old,m) -> old | m);
    }

    public void clear(int bitIndex) {
        int wordIndex = wordIndex(bitIndex);

        long mask = ~(1L << bitIndex);

        words.computeIfPresent(wordIndex, (k,v) -> v & mask);

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
        StringJoiner joiner = new StringJoiner(
                ", ",
                "[",
                "]"
        );
        for (int i = 0;;) {
            int t = nextSetBit(i);
            if (t < 0)
                break;
            joiner.add(Integer.toString(t));
            i = t + 1;
        }
        return joiner.toString();
    }

    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }
    static final class LongCell {
        volatile long value;

        public LongCell(long val) {
            this.value = val;
        }

        long getAndBitwiseOr(long t) {
            return (long) VALUE.getAndBitwiseOr(this, t);
        }
        private static final VarHandle VALUE;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VALUE = l.findVarHandle(LongCell.class, "value", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}
