package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.LL_Result;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.AtomicTransferArray;

import java.util.Arrays;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }

    public static class LockResizeArray {
        private static final int MIN_CAPACITY = 1;
        private volatile Integer[] array = new Integer[2];

        public synchronized Integer set(int i, Integer s) {
            return array[i] = s;
        }
        public void resize(int size) {
            Integer[] newArr = prepareArray(size);
            synchronized (this) {
                for (int i = 0; i < Math.min(size, array.length); ++i) {
                    Integer d = array[i];
                    newArr[i] = d == null ? 0 : d;
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

    public static class MyAtomicResizeArray extends AtomicTransferArray<Integer> {

        public MyAtomicResizeArray() {
            super(2);
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
            set(0, 1);
        }
        @Actor
        public void actor2() {
            resize(5);
        }
        @Actor
        public void actor3() {
            set(1, 2);
        }
        @Arbiter
        public void actor4(L_Result l) {
            l.r1 = getResult();
        }
    }
}