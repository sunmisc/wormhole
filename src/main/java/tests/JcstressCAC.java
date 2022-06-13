package tests;

import flempton.utils.concurrent.ConcurrentArrayCells;
import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;

public class JcstressCAC {

    public static void main(String[] args) throws Exception {Main.main(args);}


    static class Array extends ConcurrentArrayCells<Integer> {
        public Array() {
            super(2);
        }
        public void put(int i) {
            set(i,i);
        }
    }


    @JCStressTest
    @Outcome(
            id = {"[0, 1, null]","[0, 1, null, null]"},
            expect = Expect.ACCEPTABLE,
            desc = "Both updates")
    @State
    public static class InsertArray extends Array {
        @Actor
        public void actor1() {
            set(1,1);
            resize(4);
        }

        @Actor
        public void actor2() {
            set(0,0);
            resize(3);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
    @JCStressTest
    @Outcome(
            id = {"[null, null, null]","[null, null, null, null]"},
            expect = Expect.ACCEPTABLE,
            desc = "Both updates"
    )
    @State
    public static class CombineArray extends Array {

        @Actor
        public void actor1() {
            set(1,1);
            resize(4);
            remove(1);
        }

        @Actor
        public void actor2() {
            set(0,0);
            resize(3);
            remove(0);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
}
