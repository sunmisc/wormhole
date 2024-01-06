package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.memory.SegmentMemory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@Threads(1)
public class ConcurrentMemorySegment {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ConcurrentMemorySegment.class.getSimpleName())
                //.addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
    private SegmentMemory<Integer> memory;
    private AtomicReferenceArray<Integer> array;


    @Benchmark
    public int writeToMemorySegment() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        memory.store(r,r);
        return r;
    }
    @Benchmark
    public int writeToArray() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        array.setRelease(r,r);
        return r;
    }
    @Benchmark
    public Integer readFromMemorySegment() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        return memory.get(r);
    }

    @Benchmark
    public Integer readFromArray() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        return array.getAcquire(r);
    }
    @Setup
    public void prepare() {
        int n = 2048;
        array = new AtomicReferenceArray<>(n);
        memory = new SegmentMemory<>(n);
        for (int i = 0; i < n; ++i) {
            memory.store(i,i);
            array.set(i,i);
        }
    }
}
