package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.LL_Result;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.AtomicTransferArray;

import java.util.Arrays;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }

    public static class LockResizeArray {
        private Integer[] array = new Integer[10];

        public synchronized Integer set(int i, Integer s) {
            return array[i] = s;
        }
        public synchronized void resize(int i) {
            array = Arrays.copyOf(array, i);
        }
        public synchronized Integer get(int i) {
            return array[i];
        }
        public synchronized String getResult() {
            return Arrays.toString(array);
        }
    }

    public static class MyAtomicResizeArray extends AtomicTransferArray<Integer> {

        public MyAtomicResizeArray() {
            super(2);
        }
        public String getResult() {
            return super.toString();
        }
    }
    @JCStressTest
    @State
    public static class JcstressTest extends LockResizeArray {
        @Actor
        public void actor1() {
            set(0, 1);
            set(1, 2);
        }
        @Actor
        public void actor4(LL_Result l) {
            l.r1 = get(0);
            l.r2 = get(1);
        }
    }
}