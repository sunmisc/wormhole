/*
package concurrent.IntAdder;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.IntAdder;

public class ConcurrentStressIntAdder {


    public static void main(String[] args) throws Exception {
        Main.main(args);
    }


    @JCStressTest
    @Outcome(id = "40", expect = Expect.ACCEPTABLE, desc = "all updates")
    @State
    public static class CombineArrayArray extends IntAdder {
        @Actor
        public void actor1() {
            for (int i = 0; i < 10; ++i) {
                add(1);
            }
        }

        @Actor
        public void actor2() {
            for (int i = 0; i < 10; ++i) {
                add(1);
            }
        }
        @Actor
        public void actor3() {
            for (int i = 0; i < 10; ++i) {
                add(1);
            }
        }
        @Actor
        public void actor4() {
            for (int i = 0; i < 10; ++i) {
                add(1);
            }
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = get();
        }
    }
}

*/
