package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayCells;

public class JcstressCAC {

    public static void main(String[] args) throws Exception {Main.main(args);}


    static class Array extends ConcurrentArrayCells<Integer> {
        public Array() {super(4);}
    }

    @JCStressTest
    @Outcome(
            id = {"[0, 1]","[0, 1, null, null]"},
            expect = Expect.ACCEPTABLE,
            desc = "Both updates")
    @State
    public static class InsertArray extends Array {
        @Actor
        public void actor1() {
            set(1,1);
            resize(12);
        }

        @Actor
        public void actor2() {
            set(0,0);
            resize(8);
        }
        @Actor
        public void actor3() {
            set(3,3);
            resize(16);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
   /* @JCStressTest
    @Outcome(
            id = {"[null, null]","[null, null, null, null]"},
            expect = Expect.ACCEPTABLE,
            desc = "Both updates"
    )
    @State
    public static class CombineArray extends Array {

        @Actor
        public void actor1() {
            set(1,1);
            resize(8);
            remove(1);
        }

        @Actor
        public void actor2() {
            set(0,0);
            resize(4);
            remove(0);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }*/
}
