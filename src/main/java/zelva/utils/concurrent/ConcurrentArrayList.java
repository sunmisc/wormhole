package zelva.utils.concurrent;

import zelva.annotation.PreviewFeature;

import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@PreviewFeature
public class ConcurrentArrayList<E> {

    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

    private static final long WORD_MASK = 0xFFFFFFFFFFFFFFFFL; // -1

    private final ConcurrentNavigableMap<Integer,Long> words =
            new ConcurrentSkipListMap<>();
    private final ConcurrentMap<Integer,E> values =
            new ConcurrentHashMap<>();


    public void add(E element) {

        NavigableMap<Integer, Long> m = words.descendingMap();

        int lastIndex = 0;

        for (Map.Entry<Integer, Long> e : m.entrySet()) {
            long bits = e.getValue();
            if (bits != 0) {
                int k = e.getKey();
                lastIndex = (k + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(bits);
                break;
            }
        }
        if (values.putIfAbsent(++lastIndex, element) == null) {
            long mask = 1L << lastIndex;

            int wordIndex = wordIndex(lastIndex);

            words.merge(wordIndex, mask, (old, msk) -> old | msk);
        }
    }

    public void remove(int i) {
        i++;
        while (true) {
            int k = -1;
            Map.Entry<Integer, Long> last = null;
            outer:
            for (int q = 0; q < i; ++q) {
                SortedMap<Integer, Long> m = words.tailMap(wordIndex(++k));
                boolean first = true;
                for (Map.Entry<Integer, Long> e : m.entrySet()) {
                    last = e;
                    long bits = e.getValue();
                    if (first) {
                        bits &= (WORD_MASK << k);
                        first = false;
                    }
                    if (bits != 0) {
                        k = (e.getKey() * BITS_PER_WORD) + Long.numberOfTrailingZeros(bits);
                        continue outer;
                    }
                }
            }
            if (last == null) return;

            int wordIndex = last.getKey();
            long val = last.getValue();

            long nextVal = val & ~(1L << k);

            if (nextVal == 0
                    ? words.remove(wordIndex, val)
                    : words.replace(wordIndex, val, nextVal)) {
                values.remove(k);
                return;
            }
        }
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


    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }
}
