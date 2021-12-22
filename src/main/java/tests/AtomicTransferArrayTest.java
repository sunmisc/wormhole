package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.State;
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
    public static class JcstressTest extends MyAtomicResizeArray {
        @Actor
        public void actor1() {
            set(0, 0);
            resize(5);
        }
        @Actor
        public void actor2() {
            set(1, 1);
            resize(8);
        }
        @Actor
        public void actor3() {
            resize(10);
            set(2, 2);
            resize(10);
        }

        @Arbiter
        public void actor4(L_Result l) {
            l.r1 = getResult();
        }
    }
}