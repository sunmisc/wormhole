package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import tests.AtomicTransferArrayTest;
import zelva.utils.MathUtils;
import zelva.utils.concurrent.ConcurrentArrayCopy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

@State(Scope.Thread)
public class BenchAtomicResizeArray {
    AtomicTransferArrayTest.MyAtomicResizeArrayCopy myArray;
    volatile Integer[] array;
    // BenchAtomicResizeArray.set  thrpt    5   93841235,124 ± 878131,067  ops/s  // mutable
    // BenchAtomicResizeArray.set  thrpt    5   54845025,267 ± 3116677,827  ops/s // sync
    // BenchAtomicResizeArray.set  thrpt    5  158724090,183 ± 9466292,903  ops/s // field

    public static void mai2n(String[] args) {
        ConcurrentArrayCopy<Integer> array = new ConcurrentArrayCopy<>(2);
        array.set(0, 0);
        array.set(1, 1);
        System.out.println(array.toString());
        array.resize(0);

        System.out.println(array.toString());
    }
    public static void inflate(Object[] src, int srcOff, Object[] dst, int dstOff, int len) {
        for (int i = 0; i < len; i++) {
            dst[dstOff++] = src[srcOff++];
        }
    }
    public static void inflateInvert(Object[] src, int srcOff, Object[] dst, int dstOff, int len) {
        srcOff += len-1;
        dstOff += len-1;
        for (int i = 0; i < len; i++) {
            dst[dstOff--] = src[srcOff--];
        }
    }
    public static void arraycopy(Object[] src, int srcPos,
                                 Object[] dest, int destPos,
                                 int length) {
        int size = length + srcPos + destPos;
        for (; srcPos < size; srcPos++, destPos++) {
            dest[destPos] = src[srcPos];
        }

    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchAtomicResizeArray.class.getSimpleName())
                .syncIterations(false)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

    final AtomicInteger i1 = new AtomicInteger();
    final AtomicInteger i2 = new AtomicInteger();

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArrayTest.MyAtomicResizeArrayCopy();
        array = new Integer[2];
        myArray.set(0, 1);
    }

    // old
    // BenchAtomicResizeArray.get  thrpt    5  812247899,768 ± 57885402,982  ops/s
    // BenchAtomicResizeArray.set  thrpt    5  155875138,773 ± 20086582,317  ops/s

    @Benchmark
    public Integer set() {
        return myArray.set(0, 2);
    }
    @Benchmark
    public Integer get() {
        return myArray.get(0);
    }


    /*@Benchmark
    public Integer growAtomicArray() {
        int i = size(i1);
        myArray.resize(i);
        return i;
    }

    @Benchmark
    public Integer growArrayIntrinsic() {
        int i = size(i2);
        synchronized (this) {
            array = Arrays.copyOf(array, i);
        }
        return i;
    }*/
    private static int size(AtomicInteger a) {
        int i = a.getAndIncrement();
        return (int) ((MathUtils._cos(i) + 2) * 10);
    }
    /*
BenchAtomicResizeArray.growArrayIntrinsic  thrpt    5  11716659,823 ± 3986127,596  ops/s
BenchAtomicResizeArray.growAtomicArray     thrpt    5    669605,318 ±  115536,898  ops/s
     */
}
