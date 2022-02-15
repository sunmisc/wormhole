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
public class BenchSinCos {

    @Param({"121.545454"})
    double val;
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchSinCos.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(true)
                .build();
        new Runner(opt).run();
    }
    @Benchmark
    public float fastSin()   {
        return MathUtils._sin((float) val);
    }
    @Benchmark
    public double javaSin() {
        return Math.sin((float) val);
    }
}
