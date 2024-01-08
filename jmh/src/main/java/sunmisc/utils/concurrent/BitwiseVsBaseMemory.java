package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.memory.BitwiseModifiableMemory;
import sunmisc.utils.concurrent.memory.BitwiseSegmentMemory;
import sunmisc.utils.concurrent.memory.ModifiableMemory;
import sunmisc.utils.concurrent.memory.SegmentMemory;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class BitwiseVsBaseMemory {

    private BitwiseModifiableMemory<Integer> bitwise;
    private ModifiableMemory<Integer> base;

    @Setup
    public void prepare() {
        bitwise = new BitwiseSegmentMemory<>(int.class);
        base    = new SegmentMemory<>();
        int def = 12;
        bitwise.store(0, def);
        base.store(0, def);
    }
    @Benchmark
    public Integer bitwiseMemoryAt() {
        return bitwise.fetch(0);
    }

    @Benchmark
    public Integer baseMemoryAt() {
        return base.fetch(0);
    }

    @Benchmark
    public int memorySegmentBitwiseOr() {
        return bitwise.fetchAndBitwiseOr(0, 23);
    }
    @Benchmark
    public int memorySegmentCasOr() {
        int current;
        do {
            current = base.fetch(0);
        } while (!base.compareAndStore(0,
                current, current | 23));
        return current;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BitwiseVsBaseMemory.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
