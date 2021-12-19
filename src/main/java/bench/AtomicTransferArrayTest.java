package bench;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
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
            super(2);
        }

        public Integer set(int i) {
            return set(i, i);
        }
        public String getResult() {
            return super.toString();
        }
    }
    @JCStressTest
    @State
    public static class JcstressTest extends AtomicTrasformerArray {
        @Actor
        public void actor1() {
            set(0, 0);
            resize(8);
        }

        @Actor
        public void actor2(L_Result l) {
            set(1, 1);
            l.r1 = getResult();
        }
    }
}