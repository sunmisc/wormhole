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
    private volatile Integer[] array;
    // BenchAtomicResizeArray.set  thrpt    5   93841235,124 ± 878131,067  ops/s  // mutable
    // BenchAtomicResizeArray.set  thrpt    5   54845025,267 ± 3116677,827  ops/s // sync
    // BenchAtomicResizeArray.set  thrpt    5  158724090,183 ± 9466292,903  ops/s // field

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

    final Object lock = new Object();

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArrayTest.MyAtomicResizeArrayCopy();
        array = new Integer[]{1, null};
        myArray.set(0, 1);
    }

    // old
    /*
     * BenchAtomicResizeArray.getAtomic           thrpt    5  812247899,768 ± 57885402,982  ops/s
     * BenchAtomicResizeArray.setAtomic           thrpt    5  155875138,773 ± 20086582,317  ops/s
     */

    /*  multi-threads
     * BenchAtomicResizeArray.getAtomic           thrpt    5   931929140,189 ± 125337882,967  ops/s
     * BenchAtomicResizeArray.getLock             thrpt    5    52531719,390 ±   3125769,453  ops/s
     * BenchAtomicResizeArray.growArrayIntrinsic  thrpt    5    20660168,499 ±   7147115,270  ops/s
     * BenchAtomicResizeArray.growAtomicArray     thrpt    5     6250135,272 ±    748360,925  ops/s
     * BenchAtomicResizeArray.setAtomic           thrpt    5  1091027493,151 ±  68489204,340  ops/s
     * BenchAtomicResizeArray.setLock             thrpt    5    51770661,933 ±   4992974,896  ops/s
     */

    /*  single-thread
     * BenchAtomicResizeArray.getAtomic           thrpt    5   987621355,942 ± 176262422,312  ops/s
     * BenchAtomicResizeArray.getLock             thrpt    5    61864535,800 ±    223318,359  ops/s
     * BenchAtomicResizeArray.growArrayIntrinsic  thrpt    5    25543897,685 ±   1903691,102  ops/s
     * BenchAtomicResizeArray.growAtomicArray     thrpt    5     7580405,338 ±     51644,805  ops/s
     * BenchAtomicResizeArray.setAtomic           thrpt    5  1153879528,722 ±  17225043,952  ops/s
     * BenchAtomicResizeArray.setLock             thrpt    5    62000093,058 ±    125966,719  ops/s
     */

    /*@Benchmark
    public Integer setAtomic() {
        return myArray.set(0, 2);
    }
    @Benchmark
    public Integer getAtomic() {
        return myArray.get(0);
    }
    @Benchmark
    public Integer setLock() {
        synchronized (lock) {
            return array[0] = 2;
        }
    }
    @Benchmark
    public Integer getLock() {
        synchronized (lock) {
            return array[0];
        }
    }*/
    @Benchmark
    public Integer growAtomicArray() {
        final int newLength = size(i1);
        myArray.resize(newLength);
        return newLength;
    }

    @Benchmark
    public Integer growArrayLock() {
        final int newLength = size(i2);
        synchronized (lock) {
            Integer[] copy = new Integer[newLength];
            Integer[] original = array;
            inflateInvert(original, 0, copy, 0,
                    Math.min(original.length, newLength));
        }
        return newLength;
    }
    private static int size(AtomicInteger a) {
        int i = a.getAndIncrement();
        return (int) ((MathUtils._cos(i) + 2) * 10);
    }
    /*
BenchAtomicResizeArray.growArrayIntrinsic  thrpt    5  11716659,823 ± 3986127,596  ops/s
BenchAtomicResizeArray.growAtomicArray     thrpt    5    669605,318 ±  115536,898  ops/s
     */
}
