package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Threads(2)
@State(Scope.Benchmark)
public class BenchAdderVsCasInc {

    @Param({"1", "15475"})
    int a;
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchAdderVsCasInc.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(false)
                .build();
        new Runner(opt).run();
    }
    private LongAdder adder;
    private AtomicInteger atomic;

    @Setup
    public void prepare() {
        adder = new LongAdder();
        atomic = new AtomicInteger();
    }

    @Benchmark
    public int add(){
        adder.add(a);
        return a;
    }
    @Benchmark
    public int incrementCas() {
        atomic.getAndAdd(a);
        return a;
    }
}
