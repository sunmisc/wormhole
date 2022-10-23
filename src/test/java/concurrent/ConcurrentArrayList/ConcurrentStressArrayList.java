package concurrent.ConcurrentArrayList;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayList;

public class ConcurrentStressArrayList {
    public static void main(String[] args) throws Exception {
        Main.main(args);
    }


    @JCStressTest
    @Outcome(id = "40", expect = Expect.ACCEPTABLE, desc = "all updates")
    @State
    public static class AddRemove extends ConcurrentArrayList<Integer> {
        @Actor
        public void actor1() {
            add(0);
            remove(0);
        }

        @Actor
        public void actor2() {
            add(0);
            remove(0);
        }


        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
}