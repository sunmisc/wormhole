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
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchSinCos.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
    @Param({"121.545454", "0.322"})
    float val;
    @Benchmark
    public float fastSin()   {
        return MathUtils._sin(val);
    }
    @Benchmark
    public double javaSin() {
        return Math.sin(val);
    }
}
