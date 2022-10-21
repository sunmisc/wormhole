/*
package concurrent.ConcurrentArrayCells;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayCells;

public class ConcurrentStressArrayCells {

    public static void main(String[] args) throws Exception {Main.main(args);}

    @JCStressTest
    @Outcome(
            id = {
                    "[null, 1, 2, 3, 4, 5, null, null, null, null, null]",
                    "[null, 1, 2, 3, 4, 5, null, null, null]",
                    "[null, null, 2, 3, 4, 5, null, null, null, null, null, null, null]",
                    "[null, null, 2, 3, 4, 5, null, null, null, null, null]",
                    "[null, null, 2, 3, 4, 5, null, null, null]"
            },
            expect = Expect.ACCEPTABLE,
            desc = "all updates"
    )
    @State
    public static class CombineArrayArray extends ConcurrentArrayCells<Integer> {
        public CombineArrayArray() { super(2); }
        @Actor
        public void actor1() {
            resize(x -> 7);
            for (int i = 0; i < 2; ++i) {
                set(i,i);
                resize(x -> 11);
            }
            resize(x -> 9);
            remove(0);
        }

        @Actor
        public void actor2() {
            resize(x -> 8);
            for (int i = 2; i < 4; ++i) {
                set(i,i);
                resize(x -> 10);
            }
            resize(x -> 13);
            remove(1);
        }
        @Actor
        public void actor3() {
            resize(x -> 8);
            for (int i = 4; i < 6; ++i) {
                set(i,i);
                resize(x -> 9);
            }
            resize(x -> 11);
            remove(0);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
}
*/
