
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
        void thread1(final II_Result r) {
            this.y = 1;
            r.r1 = this.x;
        }

        @Actor
        void thread2(final II_Result r) {
            this.x = 1;
            r.r2 = this.y;
        }
    }
    private static class Container {
        volatile int x,y;
    }
    public static void main(final String[] args) throws Exception {
        Main.main(args);
    }
}

