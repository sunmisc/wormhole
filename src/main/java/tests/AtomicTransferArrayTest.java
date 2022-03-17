package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayCopy;

import java.util.Arrays;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }

    public static class LockResizeArray {
        private static final int MIN_CAPACITY = 1;
        private volatile Integer[] array = new Integer[3];

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
            super(3);
        }
        public String getResult() {
            return super.toString();
        }
    }
    @JCStressTest
    @State
    public static class JcstressTest extends LockResizeArray {
        @Actor
        public void actor1() {
            set(0, 0);
            resize(4);
            set(2, 2);
            resize(16);
        }

        @Actor
        public void actor2() {
            set(1, 1);
            resize(4);
            set(3, 3);
            resize(16);
            set(2, null);
            resize(17);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}