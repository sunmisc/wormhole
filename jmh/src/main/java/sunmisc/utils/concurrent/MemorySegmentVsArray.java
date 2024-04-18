package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.memory.ArrayMemory;
import sunmisc.utils.concurrent.memory.ModifiableMemory;
import sunmisc.utils.concurrent.memory.ReferenceSegmentMemory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 6, time = 1)
@Fork(1)
@Threads(1)
public class MemorySegmentVsArray {

    private static final int SIZE = 2048;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MemorySegmentVsArray.class.getSimpleName())
                //.addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }

    public enum ContainerType {
        MALLOC , ARRAY
    }
    private @Param ContainerType containerType;

    private ModifiableMemory<Integer> memory;

    @Benchmark
    public Integer read() {
        return memory.fetch(2);
    }

    @Benchmark
    public int write() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        memory.store(r,r);
        return r;
    }

    @Setup
    public void prepare() {
        memory = switch (containerType) {
            case MALLOC -> {
                ModifiableMemory<Integer> mem
                        = new ReferenceSegmentMemory<>();
                mem.realloc(SIZE);
                yield mem;
            }
            case ARRAY -> new ArrayMemory<>(SIZE);
        };
    }
}
