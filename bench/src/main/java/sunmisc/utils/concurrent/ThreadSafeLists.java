package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.lists.ConcurrentArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(2)
public class ThreadSafeLists {
    private static final int SIZE = 50;
    private @Param ListType type;

    private List<Integer> list;
    private List<Integer> mismatch;

    public enum ListType { SYNC, OPTIMISTIC }

    public static void main(final String[] args) throws RunnerException{
        new Runner(new OptionsBuilder()
                .include(ThreadSafeLists.class.getSimpleName())
                .build()
        ).run();
    }

    @Setup
    public void init() {
        this.list = switch (this.type) {
            case SYNC -> Collections.synchronizedList(new ArrayList<>());
            case OPTIMISTIC -> new ConcurrentArrayList<>();
        };
        for (int i = 0; i < SIZE; ++i) {
            this.list.add(i);
        }
        this.mismatch = ThreadLocalRandom.current()
                .ints(SIZE, 0, SIZE)
                .boxed()
                .toList();
    }
    @Benchmark
    public Integer read() {
        final int r = ThreadLocalRandom.current().nextInt(SIZE);
        return this.list.get(r);
    }

    @Benchmark
    public Integer getFirst() {
        return this.list.getFirst();
    }

    @Benchmark
    public boolean containsAs() {
        return this.list.containsAll(this.mismatch);
    }

    @Benchmark
    public int iterator() {
        int sum = 0;
        final Iterator<Integer> itr = this.list.listIterator();
        while (itr.hasNext()) {
            sum += itr.next();
        }
        return sum;
    }

    @Benchmark
    public int addAndRemove() {
        final Integer r = (int) System.currentTimeMillis();
        this.list.add(r);
        this.list.remove(r);
        return r;
    }
}
