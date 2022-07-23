package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BenchAddVsCas {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchAddVsCas.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    @Threads(Threads.MAX)
    public static class MaxWorkers extends Shared {
        @Benchmark public int cas() { return casIncrement(); }
        @Benchmark public int add() { return getAndAdd(); }
    }
    @State(Scope.Thread)
    public static class _1Worker extends Shared {
        @Benchmark public int cas() { return casIncrement(); }
        @Benchmark public int add() { return getAndAdd(); }
    }

    static class Shared {
        private final AtomicInteger a = new AtomicInteger();
        private final AtomicInteger b = new AtomicInteger();


        int getAndAdd() {
            return a.addAndGet(1);
        }
        int casIncrement() {
            return b.updateAndGet(x -> x + 1);
        }
    }
}
