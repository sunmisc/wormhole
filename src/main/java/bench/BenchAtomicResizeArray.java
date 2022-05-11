package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import tests.AtomicTransferArrayTest;
import zelva.utils.MathUtils;
import zelva.utils.concurrent.ConcurrentArrayCopy;
import zelva.utils.concurrent.ConcurrentArrayCopy1;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

@State(Scope.Benchmark)
public class BenchAtomicResizeArray {
    ConcurrentArrayCopy<Integer> myArray1;
    ConcurrentArrayCopy1<Integer> myArray2;
    private volatile AtomicReferenceArray<Object> array;


    public static void inflate(Object[] src, int srcOff, Object[] dst, int dstOff, int len) {
        for (int i = 0; i < len; i++) {
            dst[dstOff++] = src[srcOff++];
        }
    }
    public static void inflateInvert(AtomicReferenceArray<Object> src, int srcOff,
                                     AtomicReferenceArray<Object> dst, int dstOff,
                                     int len) {
        srcOff += len-1;
        dstOff += len-1;
        for (int i = 0; i < len; i++, dstOff--, srcOff--) {
            dst.set(dstOff, src.get(srcOff));
            src.compareAndSet(srcOff, null, Integer.MAX_VALUE);
            // 12514542,853 ops/s
            // 12429386
        }
    }

    public static void main(String[] args) throws RunnerException {
        ConcurrentArrayCopy<Integer> array = new ConcurrentArrayCopy<>(
                new Integer[]{0,1,2,3}
        );
        array.resize(1, 0, 4);
        System.out.println(array);
/*       Options opt = new OptionsBuilder()
                .include(BenchAtomicResizeArray.class.getSimpleName())
                .syncIterations(false)
                .forks(1)
                .build();
        new Runner(opt).run();*/
    }

    final AtomicInteger i1 = new AtomicInteger();
    final AtomicInteger i2 = new AtomicInteger();

    final Object lock = new Object();

    @Setup
    public void prepare() {
        myArray1 = new ConcurrentArrayCopy<>(3);
        myArray2 = new ConcurrentArrayCopy1<>(3);
        array = new AtomicReferenceArray<>(new Integer[]{1, null});
        myArray1.set(0, 1);
        myArray2.set(0, 1);
    }

    @Benchmark
    public Integer growAtomicArray1() {
        final int newLength = size(i1);
        myArray1.resize(newLength);
        return newLength;
    }
    @Benchmark
    public Integer growAtomicArray2() {
        final int newLength = size(i2);
        myArray2.resize(newLength);
        return newLength;
    }

    /*@Benchmark
    public Integer growArrayLock() {
        final int newLength = size(i2);
        AtomicReferenceArray<Object> copy = new AtomicReferenceArray<>(new Integer[newLength]);
        synchronized (lock) {
            AtomicReferenceArray<Object> original = array;
            inflateInvert(original, 0, copy, 0,
                    Math.min(original.length(), newLength));
        }
        return newLength;
    }*/
    private static int size(AtomicInteger a) {
        int i = a.getAndIncrement();
        return (int) ((MathUtils._cos(i) + 2) * 10);
    }
}
