
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
    @Outcome(id = {"0, 0"}, expect = FORBIDDEN, desc = "Now forbidden")
    @Outcome(expect = ACCEPTABLE)
    public static class Fences extends Container {


        @Actor
        void thread1(II_Result r) {
            y = 1;
            r.r1 = x;
        }

        @Actor
        void thread2(II_Result r) {
            x = 1;
            r.r2 = y;
        }
    }
    private static class Container {
        volatile int x,y;
    }
    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
}

