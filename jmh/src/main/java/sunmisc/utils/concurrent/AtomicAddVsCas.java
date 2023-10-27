package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class AtomicAddVsCas {

    private AtomicInteger x;

    @Setup
    public void prepare() {
        x = new AtomicInteger();
    }

    @Benchmark
    @Threads(1)
    public int testAddAndGet() {
        return testAddAndGetContended();
    }

    @Benchmark
    @Threads(Threads.MAX)
    public int testAddAndGetContended() {
        int i;
        do {
            i = x.getOpaque();
        } while (!x.weakCompareAndSetVolatile(i, i + 1));
        return i;
    }

    @Benchmark
    @Threads(1)
    public int getAndAdd() {
        return x.getAndIncrement();
    }

    @Benchmark
    @Threads(Threads.MAX)
    public int getAndAddContended() {
        return x.getAndIncrement();
    }



    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicAddVsCas.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
