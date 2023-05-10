package utils.concurrent.IntAdder;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.IntAdder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(Threads.MAX)
public class BenchIntAdder {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchIntAdder.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    private AtomicInteger atomicInteger;
    private IntAdder intAdder;
    private LongAdder longAdder;

    @Setup
    public void prepare() {
        atomicInteger = new AtomicInteger();
        intAdder = new IntAdder();
        longAdder = new LongAdder();
    }

    @Benchmark
    public int intAdder() {
        int d = 1;
        intAdder.add(d);
        return d;
    }
    @Benchmark
    public int longAdder() {
        int d = 1;
        longAdder.add(d);
        return d;
    }

    @Benchmark
    public int contendedIncrement() {
        int d = 1;
        atomicInteger.getAndAdd(d);
        return d;
    }


}
