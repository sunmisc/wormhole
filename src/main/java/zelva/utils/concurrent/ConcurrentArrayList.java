package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ConcurrentArrayList<E> {
    private static final long WORD_MASK = 0xffffffffffffffffL;
    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
    private volatile int sizeCtl;
    public final ConcurrentArrayCells<Node<E>> array =
            new ConcurrentArrayCells<>(5);


    public void add(E element) {
        for (int index;;) {
            index = (int) SIZECTL.getAndAdd(this, 1);

            int segment = segmentAt(index);
            long mask = 1L << index;

            Node<E> p = array.cae(index, null, new Node<>(element, 0));
            if (p != null && !VAL.weakCompareAndSet(p, null, element)) {
                continue;
            }

            Node<E> n = array.cae(segment, null, new Node<>(null, mask));
            if (n != null)
                BITS.getAndBitwiseOr(n, mask);
            return;
        }
    }
    public void remove(int index) {
        int q = segmentAt(index);

        Node<E> node = array.get(q);
        if (node == null) return;

        for (long bits = node.bits & (WORD_MASK << index); q < sizeCtl;) {
            if (bits != 0) {
                int idx = (q * BITS_PER_WORD) + Long.numberOfTrailingZeros(bits);

                if (BITS.weakCompareAndSet(node, bits, bits & ~(1L << idx))) {
                    Node<E> n = array.get(idx);
                    if (n != null)
                        n.element = null; // help gc
                    return;
                }
            } else {
                q++;
            }
            node = array.get(q);
            if (node == null) return;
            bits = node.bits;
        }
    }

    private static int segmentAt(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

    private static class Node<E> {
        volatile long bits;
        volatile E element;

        Node(E element) {
            this(element,0);
        }

        Node(E element, long bits) {
            this.element = element;
            this.bits = bits;
        }

        @Override
        public String toString() {
            return String.valueOf(element);
        }
    }


    @Override
    public String toString() {
        int ordinal = nextSetBit(0);
        StringBuilder builder = new StringBuilder();
        while (ordinal != -1 && ordinal < sizeCtl) {
            Node<E> n = array.get(ordinal);
            if (n != null) {
                builder.append(n).append(' ');
            }
            ordinal = nextSetBit(ordinal + 1);
        }
        return builder.toString();
    }

    public int nextSetBit(int fromIndex) {
        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);

        int u = segmentAt(fromIndex);

        Node<E> n = array.get(u);
        if (n == null)
            return -1;


        for (long word = n.bits & (WORD_MASK << fromIndex); u < sizeCtl;) {
            if (word == 0)
                u++;
            else
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            n = array.get(u);
            if (n == null) break;
            word = n.bits;
        }
        return -1;
    }


    private static final VarHandle BITS,VAL,SIZECTL;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(Node.class, "element", Object.class);
            BITS = l.findVarHandle(Node.class, "bits", long.class);
            SIZECTL = l.findVarHandle(ConcurrentArrayList.class, "sizeCtl", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
