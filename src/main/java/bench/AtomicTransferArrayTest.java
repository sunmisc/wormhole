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
        }

        @Actor
        public void actor2(L_Result l) {
            set(1, 1);
            l.r1 = getResult();
        }
    }
}