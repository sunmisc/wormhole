package sunmisc.utils.math;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 12, time = 1)
@Threads(1)
@Fork(1)
public class SinCos {
    @Param({"121.545454", "0.322"})
    float val;
    final QTrigonometry trigonometry = new QTrigonometry();


    @Benchmark
    public float fastSin()   {
        return trigonometry.sin(val).floatValue();
    }
    @Benchmark
    public double javaSin() {
        return Math.sin(val);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SinCos.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
