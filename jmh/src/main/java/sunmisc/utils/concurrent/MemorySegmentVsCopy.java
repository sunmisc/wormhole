package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.memory.ImmutableSegmentsMemory;
import sunmisc.utils.concurrent.memory.ModifiableMemory;
import sunmisc.utils.concurrent.memory.ReferenceSegmentMemory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 6, time = 1)
@Threads(1)
@Fork(1)
public class MemorySegmentVsCopy {
    private static final int SIZE = 1 << 16;
    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(MemorySegmentVsCopy.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
    private ModifiableMemory<Integer> memory;
    private ImmutableSegmentsMemory<Integer> experimental;

    @Benchmark
    public Integer realloc() {
        final int r = ThreadLocalRandom.current().nextInt(1, SIZE);
        this.memory = this.memory.realloc(r);
        return r;
    }

    @Benchmark
    public int copy() {
        final int r = ThreadLocalRandom.current().nextInt(1, SIZE);
        this.experimental = this.experimental.realloc(r);
        return r;
    }

    @Setup
    public void prepare() {
        final ModifiableMemory<Integer> mem
                        = new ReferenceSegmentMemory<>();
        final ImmutableSegmentsMemory<Integer> exp
                = new ImmutableSegmentsMemory<>(SIZE);

        mem.realloc(SIZE);
        this.memory = mem;
        this.experimental = exp;

        for (int i = 0; i < SIZE - 1; ++i) {
            mem.store(i,i);
            exp.store(i,i);
        }

    }
}
