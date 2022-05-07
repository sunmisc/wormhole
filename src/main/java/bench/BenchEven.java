package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.MathUtils;

@State(Scope.Thread)
public class BenchEven {

    @Param({"5", "88", "154754"})
    int a;
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchEven.class.getSimpleName())
                .forks(1)
                .syncIterations(true)
                .build();
        new Runner(opt).run();
    }

    @Benchmark
    public boolean testFast()   {
        return MathUtils.isEven(a);
    }
    @Benchmark
    public boolean testSimple() {
        return a % 2 == 0;
    }
}
