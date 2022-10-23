package utils.concurrent.IntAdder;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.concurrent.IntAdder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Threads(Threads.MAX)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BenchIntAdder {

    /*
     * Benchmark                          Mode  Cnt       Score        Error   Units
     * IntAdder.BenchIntAdder.increment  thrpt    5   68179,264 ±   2191,847  ops/ms
     * IntAdder.BenchIntAdder.intAdder   thrpt    5  689065,815 ±   8891,179  ops/ms
     * IntAdder.BenchIntAdder.longAdder  thrpt    5  538646,352 ± 152776,330  ops/ms
     */

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchIntAdder.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .build();
        new Runner(opt).run();
    }

    private final AtomicInteger atomicInteger
            = new AtomicInteger();
    private final IntAdder intAdder
            = new IntAdder();
    private final LongAdder longAdder
            = new LongAdder();

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
    public int increment() {
        int d = 1;
        atomicInteger.getAndAdd(d);
        return d;
    }


}
