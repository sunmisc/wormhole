
package misc;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

public class Fence {


    @JCStressTest
    @State
    @Outcome(id = {"0, 0", "1, 1"}, expect = ACCEPTABLE, desc = "Boring")
    @Outcome(id = "0, 1",           expect = ACCEPTABLE, desc = "Plausible")
    @Outcome(id = "1, 0",           expect = FORBIDDEN,  desc = "Now forbidden")
    public static class Fences extends TestFest {


        @Actor
        void thread1() {
            Object[] o = new Object[1];
            o[0] = "1232";
            arr = o;
            size = 1;
        }

        @Actor
        void thread2(II_Result r) {
            r.r1 = size;
            r.r2 = arr.length;
        }
    }
    private static class TestFest {
        Object[] arr = new Object[0];
        volatile int size;
    }
    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
}

