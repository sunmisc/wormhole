package buffer;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import sunmisc.utils.concurrent.memory.SegmentMemory;

import java.util.Arrays;
import java.util.StringJoiner;

public class SegmentMemoryTest {


    @JCStressTest
    @Outcome(
            id = "x",
            expect = Expect.FORBIDDEN)
    @State
    public static class ExpandReduce
            extends SegmentMemory<Integer> {

        @Actor
        public void actor1() {
            realloc(12);
            store(15, 1);
        }
        @Actor
        public void actor2() {
            realloc(64);
            store(15, 2);
        }
        @Actor
        public void actor3() {
            realloc(28);
            store(15, 3);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            StringJoiner joiner = new StringJoiner(
                    ", ", "[", "]");
            joiner.add(length() + " " + fetch(15));
            s.r1 = joiner.toString();
        }
    }
    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
    private static class SynchronousBuffer<T> {

        private volatile Object[] val = new Object[2];

        public synchronized void realloc(int size) {
            val = Arrays.copyOf(val, sizeFor(size));
        }
        public synchronized void store(int i, T x) {
            val[i] = x;
        }
        public synchronized T get(int i) {
            return (T) val[i];
        }
        public synchronized int length() {
            return val.length;
        }
        private static final int MAXIMUM_CAPACITY = 1 << 30;
        private static int sizeFor(int c) {
            int n = -1 >>> Integer.numberOfLeadingZeros(c - 1);
            return (n < 0) ? 1
                    : (n >= MAXIMUM_CAPACITY)
                    ? MAXIMUM_CAPACITY : n + 1;
        }
        @Override
        public synchronized String toString() {
            return Arrays.toString(val);
        }
    }
}
