package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class CowSortVsStampedLock {
    private List<Integer> cow;

    private List<Integer> lockList;
    private StampedLock lock;

    private List<Integer> col;


    @Benchmark
    public void addInCow() {
        cow.addAll(0, col);

        cow.clear();
    }
    @Benchmark
    public Integer getCow() {
        return cow.get(0);
    }
    @Benchmark
    public Integer getOptimisticLock() {
        long stamp = lock.tryOptimisticRead();
        try {
            for (; ; stamp = lock.readLock()) {
                if (stamp == 0L)
                    continue;
                // possibly racy reads
                Integer o = lockList.get(0);
                if (!lock.validate(stamp))
                    continue;
                return o;
            }
        } finally {
            if (StampedLock.isReadLockStamp(stamp))
                lock.unlockRead(stamp);
        }
    }
    @Benchmark
    public void addInLock() {
        long stamp = lock.writeLock();
        try {
            lockList.addAll(0, col);
        } finally {
            lock.unlockWrite(stamp);
        }
        stamp = lock.writeLock();
        try {
            lockList.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    @Setup
    public void prepare() {
        lockList = new ArrayList<>();
        cow = new ArrayList<>();
        lock = new StampedLock();
        col = new ArrayList<>();

        cow.add(0);
        lockList.add(0);
        for (int i = 0; i < 256; ++i) {
            col.add(i);
        }
    }


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CowSortVsStampedLock.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
