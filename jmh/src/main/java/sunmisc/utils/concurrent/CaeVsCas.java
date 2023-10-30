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
public class CaeVsCas {


    private AtomicInteger x,y;


    @Benchmark
    @Threads(8)
    public boolean cas8Threads() {
        int a = x.getOpaque();
        return x.compareAndSet(a, a + 1);
    }
    @Benchmark
    @Threads(1)
    public boolean cas1Thread() {
        int a = x.getOpaque();
        return x.compareAndSet(a, a + 1);
    }
    @Benchmark
    @Threads(8)
    public boolean cae8Threads() {
        int a = x.getOpaque();
        return x.compareAndExchange(a, a + 1) == a;
    }
    @Benchmark
    @Threads(1)
    public boolean cae1Thread() {
        int a = x.getOpaque();
        return x.compareAndExchange(a, a + 1) == a;
    }

    @Setup
    public void prepare() {
        x = new AtomicInteger();
        y = new AtomicInteger();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CaeVsCas.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

}
