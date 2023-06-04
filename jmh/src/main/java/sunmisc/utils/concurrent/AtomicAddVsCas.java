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
@Threads(Threads.MAX)
@Fork(1)
public class AtomicAddVsCas {

    private AtomicInteger x;

    @Setup
    public void prepare() {
        x = new AtomicInteger();
    }

    @Benchmark
    public int testAddAndGet() {
        return x.getAndUpdate(x -> x + 1);
    }

    @Benchmark
    public int testLambdaAddAndGet() {
        return x.getAndIncrement();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicAddVsCas.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
