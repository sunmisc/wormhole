package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.sets.ConcurrentBitSet;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class BitSets {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BitSets.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    private ConcurrentBitSet bitSet;

    @Setup
    public void init() {
        bitSet = new ConcurrentBitSet();
    }
    @Benchmark
    public int set() {
        int i = ThreadLocalRandom.current().nextInt(0, 1024);
        bitSet.add(i);
        return i;
    }
}
