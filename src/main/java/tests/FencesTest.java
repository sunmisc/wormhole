package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import java.lang.invoke.VarHandle;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

public class FencesTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
    @JCStressTest
    @Outcome(id = {"0, 0", "1, 1"}, expect = ACCEPTABLE, desc = "Boring")
    @Outcome(id =  "0, 1",          expect = ACCEPTABLE, desc = "Plausible")
    @Outcome(id =  "1, 0",          expect = FORBIDDEN,  desc = "Now forbidden")
    @State
    public static class AcqRelFences extends Container {
        @Actor
        void thread1() {
            x = 1;
            VarHandle.releaseFence();
            y = 1;
        }
        @Actor
        void thread2(II_Result r) {
            r.r1 = y;
            VarHandle.acquireFence();
            r.r2 = x;
        }
    }

    static class Container {
        int x,y;
    }
}
