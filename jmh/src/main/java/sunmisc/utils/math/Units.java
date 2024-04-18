package sunmisc.utils.math;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.world.CachedUnit;
import sunmisc.utils.world.Unit;
import sunmisc.utils.world.math.base.Add;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class Units {
    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        Options opt = new OptionsBuilder()
                .include(Units.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        Runner runner = new Runner(opt);
        runner.run();
    }
    private static final Number[] MAX = new Number[]{Long.MAX_VALUE};
    private static final Number[] MIN = new Number[]{Long.MIN_VALUE};
    private static final Unit ADD = () -> MAX;
    private static final Unit SUB = () -> MIN;

    private static final BigInteger ADD_B = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger SUB_B = BigInteger.valueOf(Long.MIN_VALUE);

    private BigInteger integer;
    private Unit unit;

    @Setup
    public void prepare() {
        integer = BigInteger.valueOf(Long.MAX_VALUE);
        unit = ADD;
    }
    @Benchmark
    public BigInteger baseline()  {

        integer = integer.add(ADD_B);

        integer = integer.subtract(SUB_B);
        return integer;
    }

    @Benchmark
    public Unit compact() {

        unit = new CachedUnit(
                new Add(
                        new CachedUnit(new Add(unit, ADD)),
                        SUB
                )
        );

        return null;
    }

}
