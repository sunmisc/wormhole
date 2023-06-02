package jcstress.lock;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import sunmisc.utils.concurrent.locks.StripedReadWriteLock;

public class ReadWriteLockTest {
    @JCStressTest
    @Outcome(
            id = {
                    "x",
            },
            expect = Expect.ACCEPTABLE,
            desc = "all updates"
    )
    @State
    public static class CombineArrayIndex extends StripedReadWriteLock.Striped32  {
        @Actor public void actor1() {
            for (int i = 0; i < 4; ++i) inc(2);
        }

        @Actor public void actor2() {
            for (int i = 0; i < 4; ++i) inc(2);
        }
        @Actor public void actor3() {
            for (int i = 0; i < 4; ++i) inc(2);
        }
        @Actor public void actor4() {
            for (int i = 0; i < 4; ++i) inc(2);
        }

        @Actor
        public void arbiter(L_Result s) {
            s.r1 = waiters();
        }
    }

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
}
