package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ContendedLinkedList {

    @Benchmark
    @Threads(Threads.MAX)
    public Integer javaConcurrentDequeContended() {
        int i = ThreadLocalRandom.current().nextInt(100);
        javaConcurrentDeque.add(i);
        return javaConcurrentDeque.poll();
    }

    @Benchmark@Threads(Threads.MAX)
    public Integer myConcurrentDequeContended() {
        int i = ThreadLocalRandom.current().nextInt(100);
        myConcurrentDeque.add(i);
        return myConcurrentDeque.poll();
    }
    @Benchmark
    @Threads(1)
    public Integer javaConcurrentDeque() {
        int i = ThreadLocalRandom.current().nextInt(100);
        javaConcurrentDeque.add(i);
        return javaConcurrentDeque.poll();
    }

    @Benchmark
    @Threads(1)
    public Integer myConcurrentDeque() {
        int i = ThreadLocalRandom.current().nextInt(100);
        myConcurrentDeque.add(i);
        return myConcurrentDeque.poll();
    }

    private UnblockingLinkedDeque<Integer> myConcurrentDeque;
    private Deque<Integer> javaConcurrentDeque;

    @Setup
    public void prepare() {
        this.myConcurrentDeque = new UnblockingLinkedDeque<>();
        this.javaConcurrentDeque = new ConcurrentLinkedDeque<>();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ContendedLinkedList.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
