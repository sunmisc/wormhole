package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayCopy;

import java.util.Arrays;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }

    public static class LockResizeArray {
        private static final int MIN_CAPACITY = 1;
        private volatile Integer[] array = new Integer[7];

        public synchronized Integer set(int i, Integer s) {
            return array[i] = s;
        }
        public void resize(int size) {
            Integer[] newArr = prepareArray(size);
            synchronized (this) {
                for (int i = 0; i < Math.min(size, array.length); ++i) {
                    newArr[i] = array[i];
                }
                array = newArr;
            }
        }
        public synchronized Integer get(int i) {
            return array[i];
        }
        public synchronized String getResult() {
            return Arrays.toString(array);
        }
        private static <E> Integer[] prepareArray(int size) {
            return new Integer[Math.max(MIN_CAPACITY, size)];
        }
    }

    public static class MyAtomicResizeArrayCopy extends ConcurrentArrayCopy<Integer> {

        public MyAtomicResizeArrayCopy() {
            super(256);
        }
        public String getResult() {
            return super.toString();
        }
    }
    @JCStressTest
    //@Outcome(id = {"[0, 1, 2, 3, 4, 5, null, null, null, null, null, null, null, null, null, null]"},
    //        expect = Expect.ACCEPTABLE)
    @State
    public static class JcstressTest extends MyAtomicResizeArrayCopy {
        @Actor
        public void actor1() {
            set(0, 0);
            resize(259);
            set(1, 1);
            resize(256);
        }

        @Actor
        public void actor2() {
            set(254, 254);
            resize(257);
            set(255, 255);
            resize(256);
        }
        @Actor
        public void actor3() {
            set(2, 2);
            resize(280);
            set(253, 253);
            resize(256);
        }
        @Actor
        public void actor4() {
            set(150, 150);
            resize(271);
            set(152, 152);
            resize(256);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}