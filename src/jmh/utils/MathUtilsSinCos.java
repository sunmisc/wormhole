package utils;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.MathUtils;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MathUtilsSinCos {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MathUtilsSinCos.class.getSimpleName())
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
