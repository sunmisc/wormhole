package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayCopy;
import zelva.utils.concurrent.LockArrayCopy;

import java.util.Arrays;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
    public static Integer[] prepareArray() {
        Integer[] arr = new Integer[16];
        Arrays.setAll(arr, i->i);
        return arr;
    }
    public static class LockResizeArrayCopy
            extends LockArrayCopy<Integer> {

        public LockResizeArrayCopy() {
            super(16);
            for (int i = 0; i < 16; ++i) {
                set(i,i);
            }
        }
        public String getResult() {
            return super.toString();
        }
    }
    public static class MyAtomicResizeArrayCopy
            extends ConcurrentArrayCopy<Integer> {

        public MyAtomicResizeArrayCopy() {
            super(16);
            for (int i = 0; i < 16; ++i) {
                set(i,i);
            }
        }
        public String getResult() {
            return super.toString();
        }
    }

    @JCStressTest
    @Outcome(id = {
            "xxx",
    }, expect = Expect.ACCEPTABLE, desc = "yees")
    @State
    public static class JcstressTest extends MyAtomicResizeArrayCopy {
        @Actor
        public void actor1() {
            set(0, null);
            resize(12);
            set(3, null);
            resize(16);
        }
        @Actor
        public void actor2() {
            set(1, null);
            resize(11);
            set(4, null);
            resize(13);
        }
        @Actor
        public void actor3() {
            set(2, null);
            resize(11);
            set(5, null);
            resize(16);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }

}