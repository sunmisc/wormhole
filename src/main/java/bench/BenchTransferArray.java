package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.MathUtils;
import zelva.utils.concurrent.ConcurrentArrayCopyForbidden;
import zelva.utils.concurrent.ConcurrentArrayCopyStride;

import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Thread)
public class BenchTransferArray {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchTransferArray.class.getSimpleName())
                .syncIterations(true)
                .forks(1)
                .measurementIterations(3)
                .build();
        new Runner(opt).run();
    }
    private final AtomicInteger r1 = new AtomicInteger();
    private final AtomicInteger r2 = new AtomicInteger();
    private final AtomicInteger r3 = new AtomicInteger();

    private final ConcurrentArrayCopyForbidden<Integer> noParallel
            = new ConcurrentArrayCopyForbidden<>(128);
    private final ConcurrentArrayCopyStride<Integer> stride
            = new ConcurrentArrayCopyStride<>(128);
    private final SynchronizedArrayResize<Integer> lockArray
            = new SynchronizedArrayResize<>(128);

    @Benchmark
    public Integer resizeNoParallel() {
        final int newLength = size(r1);
        noParallel.resize(newLength);
        return newLength;
    }
    @Benchmark
    public Integer resizeLock() {
        final int newLength = size(r2);
        lockArray.resize(newLength);
        return newLength;
    }
    @Benchmark
    public Integer resizeStride() {
        final int newLength = size(r3);
        stride.resize(newLength);
        return newLength;
    }
    private static int size(AtomicInteger a) {
        int i = a.getAndIncrement();
        return (int) ((MathUtils._cos(i) + 2) * 10);
    }
}
