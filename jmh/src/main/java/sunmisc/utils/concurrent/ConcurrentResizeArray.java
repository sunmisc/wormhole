package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.MathUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Threads(1)
@Fork(1)
public class ConcurrentResizeArray {
    static final int INIT_CAPACITY = 1_000_000;
    static final double SC = Math.PI * 5;
    static final int DELTA = 700_000;

    private AtomicInteger adder;
    private UnblockingArrayBuffer<Integer> unblocking;


    @Setup
    public void prepare() {
        adder = new AtomicInteger();
        unblocking = new UnblockingArrayBuffer<>(INIT_CAPACITY);
    }

    @Benchmark
    public int resizeConcurrentSeq() {
        int n = getNextSize(adder.getAndIncrement());
        unblocking.resizeTest(x -> n, false);
        return n;
    }

    @Benchmark
    public int resizeConcurrentParallel() {
        int n = getNextSize(adder.getAndIncrement());
        unblocking.resizeTest(x -> n, true);
        return n;
    }

    static int getNextSize(int a) {
        return (int) ((MathUtils._cos(a/SC) + 1) * DELTA);
    }
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ConcurrentResizeArray.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
