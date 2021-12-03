package bench;

import com.sun.jna.WString;
import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.concurrent.AtomicTransferArray;


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
    // @Outcome(id = "[0, 1, 2, 3]", expect = ACCEPTABLE, desc = "Boring")
    @State
    public static class JcstressTest extends AtomicTrasformerArray {
        @Actor
        public void actor1() {
            set(0, 1);
            resize(7);
        }

        @Actor
        public void actor2() {
            resize(5);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}
