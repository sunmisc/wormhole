package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentCells;
import zelva.utils.concurrent.ConcurrentHashCells;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;



public class ConcurrentCellsStress {

    public static void main(String[] args) throws Exception {
        ConcurrentHashCells<Integer,String> chc = new ConcurrentHashCells<>();
        ConcurrentHashMap<Integer,String> chm = new ConcurrentHashMap<>();
        for (int i = 0; i < 8; ++i) {
            chc.put(i+(i << 1), "test");
            chm.put(i+(i << 1), "test");
        }
        System.out.println(chc);
        System.out.println(chm);

        System.out.println(chc.get(6));
        System.out.println(chm.get(6));
    }
    public static void main0(String[] args) {
        int h = 231 & 0x7FFFFFFF;
        System.out.println(pos(8, h));

        System.out.println(pos(16, h));
    }
    static int pos(int n, int h) {
        return (h % (n + 1));
    }

    @JCStressTest
    @Outcome(id = {
            "xxx",
    }, expect = Expect.ACCEPTABLE, desc = "yees")
    @State
    public static class JcstressTest extends ConcurrentCells {
        @Actor
        public void actor1() {
            set(0, 0);
            set(4, 4);
        }

        @Actor
        public void actor2() {
            set(16, 16);
            set(8, 8);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = super.toString();
        }
    }
}
