package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayCopy;
import zelva.utils.concurrent.ConcurrentArrayCopy1;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

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
                Integer d = array[i];
                array[i] = s;
                return d;
            }
        }
        public void resize(int srcPos, int destPos, int size) {
            Integer[] newArr = prepareArray(size);
            synchronized (lock) {
                System.arraycopy(
                        array, srcPos,
                        newArr, destPos,
                        Math.min(array.length-srcPos, size-destPos)
                );
                array = newArr;
            }
        }
        public void resize(int size) {
            resize(0,0,size);
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

    public static class MyAtomicResizeArrayCopy
            extends ConcurrentArrayCopy<Integer> {

        public MyAtomicResizeArrayCopy() {
            super(4);
        }
        public String getResult() {
            return super.toString();
        }
    }


   /* @JCStressTest
    @Outcome(id = {
            "xxx",
    }, expect = Expect.ACCEPTABLE, desc = "yees")
    @State
    public static class JcstressTest extends MyAtomicResizeArrayCopy {
        @Actor
        public void actor1() {
            set(0, 0);
            resize(12);
            set(3, 3);
            resize(16);
        }

        @Actor
        public void actor2() {
            set(1, 1);
            resize(11);
            set(4, 4);
            resize(13);
        }
        @Actor
        public void actor3() {
            set(2, 2);
            resize(11);
            set(5, 5);
            resize(16);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }*/


    @JCStressTest
    @Outcome(id = {
            "1", "null"
    }, expect = Expect.ACCEPTABLE, desc = "yees")
    @State
    public static class JcstressTest extends MyAtomicResizeArrayCopy {
        @Actor
        public void actor1() {
            set(0, 0);
            resize(12);
        }

        @Actor
        public void actor2(L_Result l) {
            l.r1 = getResult();
        }
    }
}