package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.sets.ConcurrentBitSet;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(BitSets.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    private static final int SIZE = 1 << 14;
    private ConcurrentBitSet concurrentBitSet;
    private ConcurrentMap<Integer, Boolean> bitSet;

    @Setup
    public void init() {
        this.concurrentBitSet = new ConcurrentBitSet();
        this.bitSet = new ConcurrentHashMap<>();
    }

    @Benchmark
    public int putInConcurrentBitSet() {
        final int delta = ThreadLocalRandom.current().nextInt(0, SIZE);
        this.concurrentBitSet.add(delta);
        return delta;
    }
    @Benchmark
    public int putInConcurrentMap() {
        final int delta = ThreadLocalRandom.current().nextInt(0, SIZE);
        this.bitSet.put(delta, true);
        return delta;
    }
}
