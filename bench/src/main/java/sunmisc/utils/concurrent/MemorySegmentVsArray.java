package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.memory.ArrayMemory;
import sunmisc.utils.concurrent.memory.ModifiableMemory;
import sunmisc.utils.concurrent.memory.SegmentsMemory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 6, time = 1)
@Threads(1)
@Fork(1)
public class MemorySegmentVsArray {

    private static final int SIZE = 1 << 13;

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(MemorySegmentVsArray.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    public enum ContainerType { MALLOC, ARRAY }

    private @Param ContainerType type;
    private ModifiableMemory<Integer> memory;

    @Setup
    public void prepare() {
        this.memory = switch (this.type) {
            case MALLOC -> new SegmentsMemory<>(SIZE);
            case ARRAY -> new ArrayMemory<>(SIZE);
        };
    }

    @Benchmark
    public Integer read() {
        final int r = ThreadLocalRandom.current().nextInt(SIZE -1);
        return this.memory.fetch(r);
    }

    @Benchmark
    public int write() {
        final int r = ThreadLocalRandom.current().nextInt(SIZE -1);
        this.memory.store(r,r);
        return r;
    }

}
