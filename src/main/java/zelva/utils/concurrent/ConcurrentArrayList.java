package zelva.utils.concurrent;

import org.jetbrains.annotations.Nullable;
import zelva.annotation.PreviewFeature;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@PreviewFeature
public class ConcurrentArrayList<E> {

    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

    /* Used to shift left or right for a partial word mask */
    private static final long WORD_MASK = 0xFFFFFFFFFFFFFFFFL; // -1

    volatile ConcurrentCells<Node<E>> table
            = new LockArrayCells<>(1);


    public void add(E element) {
        while (true) {
            int bitIndex = lastSetBit() + 1;

            if (bitIndex >= table.length() - 1) {
                table.resize(n -> bitIndex >= n - 1 ? n << 1 : n);
            }
            if (set(bitIndex, element)) {
                return;
            }
        }
    }
    public boolean set(int bitIndex, E e) {
        int wordIndex = segmentAt(bitIndex);

        long mask = 1L << bitIndex;

        Node<E> p = table.get(bitIndex);

        if (p != null || (p = table.cae(
                bitIndex,
                null,
                new Node<>(e)
        )) != null) {
            p.value = e; // todo: fixed race
        }
        Node<E> n = table.get(wordIndex);

        if (n == null) {
            n = table.cae(wordIndex, null, new Node<>(mask));
        }
        if (n != null) {
            BITS.getAndBitwiseOr(n, mask);
        }
        return true;
    }
    public void remove(int i) {
        long bt = 0; Node<E> w = null;
        for (int start = -1, n = i + 1;;) {
            outer : for (int q = 0; q < n; ++q) {
                int u = segmentAt(++start);
                if (u >= table.length())
                    break;
                bt = (w = table.get(u)) == null ? 0 : w.bits;
                for (long bits = bt & WORD_MASK << start; ; ) {
                    if (bits != 0) {
                        start = (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(bits);
                        continue outer;
                    } else if (++u >= table.length()) {
                        return;
                    } else if ((w = table.get(u)) == null) {
                        continue;
                    }
                    bits = w.bits;
                }
            }
            Node<E> p = table.get(start);
            if (w == null || BITS.weakCompareAndSet(
                    w, bt, bt & ~(1L << start))) {
                if (p != null) p.value = null; // help gc
                return;
            }
        }
    }

    private int lastSetBit() {
        int n = table.length()-1;
        int u = segmentAt(n);
        for (long bits; u >= 0; u--) {
            Node<E> w = table.get(u);
            if (w != null && (bits = w.bits) != 0)
                return (u+1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(bits);
        }
        return -1;
    }

    private int nextSetBit(int start) {
        int u = segmentAt(start);
        if (u >= table.length()) return -1;
        Node<E> w = table.get(u);
        if (w == null) w = new Node<>(null, 0);
        for (long bits = w.bits & (WORD_MASK << start);;) {
            if (bits != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(bits);
            else if (++u >= table.length() ||
                    (w = table.get(u)) == null) {
                return -1;
            }
            bits = w.bits;
        }
    }

    private static int segmentAt(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

    static class Node<E> {
        volatile long bits;
        volatile @Nullable E value;

        Node(E value) { this.value = value; }
        Node(long bits) { this.bits = bits; }

        Node(E value, long bits) {
            this.value = value;
            this.bits = bits;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < table.length();) {
            int t = nextSetBit(i);
            if (t < 0) break;
            builder.append(table.get(t)).append(' ');
            i = t + 1;
        }
        return builder.toString();
    }

    private static final VarHandle BITS;
    private static final VarHandle VAL;
    private static final VarHandle TAB;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BITS = l.findVarHandle(Node.class, "bits", long.class);
            VAL = l.findVarHandle(Node.class, "value", Object.class);
            TAB = l.findVarHandle(ConcurrentArrayList.class, "table", ConcurrentCells.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
