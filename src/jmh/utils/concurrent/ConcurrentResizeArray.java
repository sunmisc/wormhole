package utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.MathUtils;
import sunmisc.utils.concurrent.ConcurrentArrayBuffer;
import sunmisc.utils.concurrent.ConcurrentIndexMap;
import sunmisc.utils.concurrent.UnblockingArrayBuffer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ConcurrentResizeArray {
    static final int INIT_CAPACITY = 1_000_000;
    static final double SC = Math.PI * 5;
    static final int DELTA = 700_000;
    private AtomicInteger x;
    private ConcurrentIndexMap<Integer> unblocking, concurrent;


    @Setup
    public void prepare() {
        x = new AtomicInteger();
        unblocking = new UnblockingArrayBuffer<>(INIT_CAPACITY);
        concurrent = new ConcurrentArrayBuffer<>(INIT_CAPACITY);
    }
    @Benchmark
    @Threads(Threads.MAX)
    public int testUnblockingResizeContended() {
        return resize(unblocking);
    }
    @Benchmark
    @Threads(Threads.MAX)
    public int testConcurrentResizeContended() {
        return resize(concurrent);
    }

    @Benchmark
    @Threads(1)
    public int testUnblockingResize1Thread() {
        return resize(unblocking);
    }
    @Benchmark
    @Threads(1)
    public int testConcurrentResize1Thread() {
        return resize(concurrent);
    }

    public int resize(ConcurrentIndexMap<Integer> arr) {
        int n = getNextSize(x.getAndIncrement());
        arr.resize(x -> n);
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
