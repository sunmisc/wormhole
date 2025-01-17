package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.NavigableSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class SkipListVsList {

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(SkipListVsList.class.getSimpleName())
                // .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }

    @State(Scope.Group)
    public static class LinkedListState {
        final Queue<Integer> list
                = new ConcurrentLinkedQueue<>();

    }
    @State(Scope.Group)
    public static class SkipListState {
        final NavigableSet<Integer> skipListSet
                = new ConcurrentSkipListSet<>();
    }

    @Benchmark
    @Group("linkedlist")
    public int add(final LinkedListState state) {
        final int i = ThreadLocalRandom.current().nextInt(100);
        state.list.add(i);
        return i;
    }

    @Benchmark
    @Group("linkedlist")
    public Integer poll(final LinkedListState state) {
        return state.list.poll();
    }

    @Benchmark
    @Group("skiplist")
    public int add(final SkipListState state) {
        final int i = ThreadLocalRandom.current().nextInt(100);
        state.skipListSet.add(i);
        return i;
    }

    @Benchmark
    @Group("skiplist")
    public Integer poll(final SkipListState state) {
        return state.skipListSet.pollFirst();
    }
}
