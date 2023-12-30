package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(Threads.MAX)
@Fork(1)
public class ConcurrentListDeque {

    @State(Scope.Group)
    public static class BaseLineState {
        final UnblockingLinkedDeque<Integer> deque
                = new UnblockingLinkedDeque<>();
    }
    @State(Scope.Group)
    public static class SynchronousQueueState {
        final UnLinkedQueue<Integer> queue
                = new UnLinkedQueue<>();
    }
    @Benchmark
    @Group("baseline")
    public int add(BaseLineState state) {
        int i = ThreadLocalRandom.current().nextInt(100);
        state.deque.add(i);
        return i;
    }

    @Benchmark
    @Group("baseline")
    public Integer poll(BaseLineState state) {
        return state.deque.poll();
    }
    @Benchmark
    @Group("synchronous")
    public int add(SynchronousQueueState state) {
        int i = ThreadLocalRandom.current().nextInt(100);
        state.queue.add(i);
        return i;
    }

    @Benchmark
    @Group("synchronous")
    public void poll(SynchronousQueueState state) {
        state.queue.poll();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ConcurrentListDeque.class.getSimpleName())
                // .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
