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
            set(0);
            resize(4);
            set(2);
            resize(4);
        }

        @Actor
        public void actor2() {
            set(1);
            resize(4);
            set(3);
            resize(4);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}
