package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(4)
@Fork(1)
public class OpaqueVsPlain {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OpaqueVsPlain.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
    private AtomicInteger x;

    @Setup
    public void prepare() {
        x = new AtomicInteger();
    }

    @Benchmark
    public int getOpaque() {
        return x.getOpaque();
    }

    @Benchmark
    public int getPlain() {
        return x.getPlain();
    }
}
