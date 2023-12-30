package buffer;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import sunmisc.utils.concurrent.ConcurrentSegmentBuffers;

import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class SegmentBuffer {
    @JCStressTest
    @Outcome(
            id = "[0, 1, 2, 3, 4, 5, 6, 7, null, null, null, null, null, null, null, null]",
            expect = Expect.ACCEPTABLE)
    @Outcome(
            id = "[0, 1, 2, 3, 4, 5, 6, 7]",
            expect = Expect.ACCEPTABLE)
    @State
    public static class CombineArrayIndex
            extends ConcurrentSegmentBuffers<Integer> {

        @Actor
        public void actor1() {
            expand();
            expand();
            expand();
            for (int i = 0; i < 8; ++i)
                set(i,i);
        }
        @Actor
        public void actor2() {
            reduce();
        }
        @Arbiter
        public void arbiter(L_Result s) {
            StringJoiner joiner = new StringJoiner(
                    ", ", "[", "]");
            for (int i = 0; i < length(); ++i)
                joiner.add(Objects.toString(get(i)));
            s.r1 = joiner.toString();
        }
    }

    @JCStressTest
    @Outcome(
            id = "[0, -2, null, null]",
            expect = Expect.ACCEPTABLE)
    @Outcome(
            id = "[0, -2]",
            expect = Expect.ACCEPTABLE)
    @State
    public static class ExpandReduce
            extends ConcurrentSegmentBuffers<Integer> {

        @Actor
        public void actor1() {
            expand();
            set(0,0);
        }
        @Actor
        public void actor2() {
            reduce();
            set(1, -2);
        }
        @Arbiter
        public void arbiter(L_Result s) {
            StringJoiner joiner = new StringJoiner(
                    ", ", "[", "]");
            for (int i = 0; i < length(); ++i)
                joiner.add(Objects.toString(get(i)));
            s.r1 = joiner.toString();
        }
    }
    @JCStressTest
    @Outcome(
            id = "[0, -2, null, null, null, null, null, null]",
            expect = Expect.ACCEPTABLE)
    @Outcome(
            id = "[0, null, null, null, null, null, null, null]",
            expect = Expect.ACCEPTABLE)
    @Outcome(
            id = "[null, -2, null, null, null, null, null, null]",
            expect = Expect.ACCEPTABLE)
    @Outcome(
            id = "[0, null, null, null]",
            expect = Expect.ACCEPTABLE)
    @Outcome(
            id = "[null, -2, null, null]",
            expect = Expect.ACCEPTABLE)
    @State
    public static class Expand
            extends ConcurrentSegmentBuffers<Integer> {

        @Actor
        public void actor1(L_Result s) {
            expand();
            set(0,0);
            arbiter(s);
        }
        @Actor
        public void actor2(L_Result s) {
            expand();
            set(1, -2);
            arbiter(s);
        }
        private void arbiter(L_Result s) {
            StringJoiner joiner = new StringJoiner(
                    ", ", "[", "]");
            for (int i = 0; i < length(); ++i)
                joiner.add(Objects.toString(get(i)));
            s.r1 = joiner.toString();
        }
    }
    public static void main(String[] args) throws Exception {
        Main.main(args);
    }

    private static class SynchronousBuffer<T> {

        private volatile Object[] val = new Object[2];

        public synchronized void expand() {
            val = Arrays.copyOf(val, val.length * 2);
        }
        public synchronized void reduce() {
            int m = Math.max(2, val.length / 2);
            val = Arrays.copyOf(val, m);
        }
        public synchronized void set(int i, T x) {
            val[i] = x;
        }
        public synchronized T get(int i) {
            return (T) val[i];
        }
        public synchronized int length() {
            return val.length;
        }
        @Override
        public synchronized String toString() {
            return Arrays.toString(val);
        }
    }
}