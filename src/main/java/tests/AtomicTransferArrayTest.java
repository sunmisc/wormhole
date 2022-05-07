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

    public static class LockResizeArrayInt {
        private static final int MIN_CAPACITY = 1;
        private final Object lock = new Object();
        private volatile Integer[] array = new Integer[3];

        public Integer set(int i, Integer s) {
            synchronized (lock) {
                return array[i] = s;
            }
        }
        public void resize(int size) {
            Integer[] newArr = prepareArray(size);
            synchronized (lock) {
                for (int i = 0, len = Math.min(size, array.length); i < len; ++i) {
                    newArr[i] = array[i];
                }
                array = newArr;
            }
        }
        public Integer get(int i) {
            synchronized (lock) {
                return array[i];
            }
        }
        public String getResult() {
            return Arrays.toString(array);
        }
        private static Integer[] prepareArray(int size) {
            return new Integer[Math.max(MIN_CAPACITY, size)];
        }
    }

    public static class MyAtomicResizeArrayCopy extends ConcurrentArrayCopy<Integer> {

        public MyAtomicResizeArrayCopy() {
            super(2);
        }
        public String getResult() {
            return super.toString();
        }
    }
    @JCStressTest
    @Outcome(id = {
            "[0, 1, 2, 3, null, null, null, null, null, null, null, null, null, null, null, null]",
            "[0, 1, 2, 3, null, null, null, null, null, null, null, null, null]0"
    }, expect = Expect.ACCEPTABLE, desc = "yees")
    @State
    public static class JcstressTest extends MyAtomicResizeArrayCopy {
        @Actor
        public void actor1() {
            set(0, 0);
            resize(12);
            set(2, 2);
            resize(16);
        }

        @Actor
        public void actor2() {
            set(1, 1);
            resize(13);
            set(3, 3);
            resize(13);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}