package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.BlockingArrayCells;
import zelva.utils.concurrent.ConcurrentArrayCells;

public class JcstressCAC {

    public static void main(String[] args) throws Exception {Main.main(args);}


    static class ArrayArray extends ConcurrentArrayCells<Integer> {
        public ArrayArray() {
            super(2);
        }
        public void put(int i) {
            set(i,i);
        }
    }


    /*@JCStressTest
    @Outcome(
            id = {"[0, 1]","[0, 1, null, null]"},
            expect = Expect.ACCEPTABLE,
            desc = "Both updates")
    @State
    public static class InsertArrayArray extends ArrayArray {
        @Actor
        public void actor1() {
            set(1,1);
            resize(4);
        }

        @Actor
        public void actor2() {
            set(0,0);
            resize(2);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }*/
    @JCStressTest
    @Outcome(
            id = {"[null, null]","[null, null, null, null]0"},
            expect = Expect.ACCEPTABLE,
            desc = "Both updates"
    )
    @State
    public static class CombineArrayArray extends ArrayArray {

        @Actor
        public void actor1() {
            resize(7);
            for (int i = 0; i < 2; ++i) {
                set(i,i);
            }
            resize(9);
        }

        @Actor
        public void actor2() {
            resize(8);
            for (int i = 2; i < 4; ++i) {
                set(i,i);
            }
            resize(10);
        }
        @Actor
        public void actor3() {
            resize(89);
            for (int i = 4; i < 6; ++i) {
                set(i,i);
            }
            resize(11);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
}
