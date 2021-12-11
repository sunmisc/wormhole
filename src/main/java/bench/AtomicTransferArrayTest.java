package bench;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.concurrent.AtomicTransferArray;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
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
            set(7, 7);
        }

        @Actor
        public void actor2() {
            set(1, 1);
            resize(9);
            set(5, 5);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}