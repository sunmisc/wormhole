package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 333, time = 1)
@Fork(1)
@Threads(4)
public class ConcurrentBuffers {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ConcurrentBuffers.class.getSimpleName())
                //.addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
    private ConcurrentSegmentBuffers<Integer> buffers;
    private AtomicReferenceArray<Integer> array;


    @Benchmark
    public int writeToBuffers() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        buffers.set(r,r);
        return r;
    }
    @Benchmark
    public int writeToArray() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        array.setRelease(r,r);
        return r;
    }
    @Benchmark
    public Integer readToBuffers() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        return buffers.get(r);
    }

    @Benchmark
    public Integer readToArray() {
        int r = ThreadLocalRandom.current().nextInt(2048);
        return array.getAcquire(r);
    }
    @Setup
    public void prepare() {
        int n = 2048;
        array = new AtomicReferenceArray<>(n);
        buffers = ConcurrentSegmentBuffers.of(n);
        for (int i = 0; i < n; ++i) {
            buffers.set(i,i);
            array.set(i,i);
        }
    }
}
