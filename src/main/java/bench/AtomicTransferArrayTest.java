package bench;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.concurrent.AtomicTransferArray;

import java.util.Arrays;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }

    public static class LockTrasformerArray {
        private Integer[] array = new Integer[10];

        public synchronized Integer set(int i, int s) {
            return array[i] = s;
        }
        public synchronized void resize(int i) {
            array = Arrays.copyOf(array, i);
        }
        public synchronized String getResult() {
            return Arrays.toString(array);
        }
    }

    public static class AtomicTrasformerArray extends AtomicTransferArray<Integer> {

        public AtomicTrasformerArray() {
            super(10);
        }

        public Integer set(int i) {
            return set(i, i);
        }
        public String getResult() {
            return super.toString();
        }
    }
    @JCStressTest
    //@Outcome(id = {"[0, 1, null, null, null, 5, null, null]"}, expect = ACCEPTABLE, desc = "Both updates.")
    @State
    public static class JcstressTest extends LockTrasformerArray {
        @Actor
        public void actor1() {
            for (int i = 0; i < 5; ++i) {
                set(i, 988);
                resize(i + 10);
                set(i, i);
            }
        }

        @Actor
        public void actor2() {
            for (int i = 5; i < 10; ++i) {
                set(i, 988);
                resize(i + 10);
                set(i, i);
            }
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}