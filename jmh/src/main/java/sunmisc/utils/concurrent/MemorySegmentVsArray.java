package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.memory.BitwiseSegmentMemory;
import sunmisc.utils.concurrent.memory.ModifiableMemory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 6, time = 1)
@Fork(1)
@Threads(1)
public class MemorySegmentVsArray {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MemorySegmentVsArray.class.getSimpleName())
                //.addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
    private ModifiableMemory<Integer> memory;
    private AtomicReferenceArray<Integer> array;

    @Benchmark
    public Integer memoryAt() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        return memory.fetch(r);
    }

    @Benchmark
    public Integer arrayAt() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        return array.get(r);
    }

    @Benchmark
    public int writeToMemorySegment() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        memory.store(r,r);
        return r;
    }
    @Benchmark
    public int writeToArray() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        array.set(r,r);
        return r;
    }

    @Setup
    public void prepare() {
        int n = 2048;
        array = new AtomicReferenceArray<>(n);
        memory = new BitwiseSegmentMemory<>(int.class);
        memory.realloc(n);
        for (int i = 0; i < n; ++i) {
            memory.store(i,i);
            array.set(i,i);
        }
    }
}
