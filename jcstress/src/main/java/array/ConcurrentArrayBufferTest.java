package array;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import sunmisc.utils.concurrent.UnblockingArrayBuffer;

public class ConcurrentArrayBufferTest {

    @JCStressTest
    @Outcome(
            id = {"x"}, expect = Expect.ACCEPTABLE, desc = "all updates"
    )
    @State
    public static class CombineArrayIndex extends UnblockingArrayBuffer<Integer> {
        public CombineArrayIndex() { super(10); }
        @Actor
        public void actor1() {
            put(5, 3);
            resize(x -> 15);
        }

        @Actor
        public void actor2() {
            put(9, 3);
            resize(x -> 20);
            put(14, 3);
        }
        @Actor
        public void actor3() {
            put(6, 3);
            resize(x -> 15);
        }

        @Actor
        public void actor4() {
            put(8, 3);
            resize(x -> 20);
            put(13, 3);
        }
        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
}
