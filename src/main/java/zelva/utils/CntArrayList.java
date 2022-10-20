package zelva.utils;

import java.util.*;

public class CntArrayList<E> {
    private final TreeMap<Integer,Long> words = new TreeMap<>();
    private final Map<Integer, E> values = new HashMap<>();

    public E get(int i) {
        int k = nextSetBit(0);
        for (int q = 0; q < i; ++q) {
            k = nextSetBit(k+1);
        }
        return values.get(k);
    }

    public void add(E element) {
        int i = lastSetBit()+1;
        if (values.put(i, element) == null) {
            set(i);
        }
    }
    public void remove(int i) {
        int k = nextSetBit(0);
        for (int q = 0; q < i; ++q) {
            k = nextSetBit(k+1);
        }
        if (k >= 0) {
            clear(k);
            values.remove(k);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (int i = 0;;) {
            int t = nextSetBit(i);
            if (t < 0) break;
            builder.append(values.get(t)).append(' ');
            i = t + 1;
        }
        return builder.toString();
    }

    // bit set

    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

    private static final long WORD_MASK = 0xFFFFFFFFFFFFFFFFL; // -1

    int size;

    private void set(int bitIndex) {
        size++;

        int wordIndex = wordIndex(bitIndex);

        long mask = 1L << bitIndex;

        words.merge(wordIndex, mask, (old,m) -> old | m);
    }
    private void clear(int bitIndex) {
        int wordIndex = wordIndex(bitIndex);

        long mask = ~(1L << bitIndex);

        words.computeIfPresent(wordIndex, (k,v) -> {
            long val = v & mask;
            return val == 0 ? null : val;
        });
    }


    private int lastSetBit() {
        NavigableMap<Integer,Long> m = words.descendingMap();

        for (Map.Entry<Integer,Long> e : m.entrySet()) {
            long bits = e.getValue();
            if (bits != 0) {
                return (e.getKey() + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(bits);
            }
        }
        return -1;
    }
    private int nextSetBit(int start) {
        SortedMap<Integer,Long> m = words.tailMap(wordIndex(start));
        boolean first = true;
        for (Map.Entry<Integer,Long> e : m.entrySet()) {
            long bits = e.getValue();
            if (first) {
                bits &= (WORD_MASK << start);
                first = false;
            }
            if (bits != 0) {
                return (e.getKey() * BITS_PER_WORD) + Long.numberOfTrailingZeros(bits);
            }
        }
        return -1;
    }

    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }
}
