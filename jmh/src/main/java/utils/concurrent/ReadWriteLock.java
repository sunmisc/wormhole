package utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.locks.StripedReadWriteLock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(Threads.MAX)
@Fork(1)
public class ReadWriteLock {
    private static final long TOKENS = 3;
    private ReentrantReadWriteLock reentrantReadWriteLock;
    private StripedReadWriteLock stripedReadWriteLock;
    private ReentrantLock lock;

    @Setup
    public void prepare() {
        reentrantReadWriteLock = new ReentrantReadWriteLock();
        stripedReadWriteLock = new StripedReadWriteLock();
        lock = new ReentrantLock();
    }
    @Benchmark
    public void simpleLock() {
        lock.lock();
        try {
            Blackhole.consumeCPU(TOKENS);
        } finally {
            lock.unlock();
        }
    }
    @Benchmark
    public void reentrantReadLock() {
        Lock r = reentrantReadWriteLock.readLock();
        r.lock();
        try {
            Blackhole.consumeCPU(TOKENS);
        } finally {
            r.unlock();
        }
    }
    @Benchmark
    public Void stripedReadLock() {
        return stripedReadWriteLock.readLock(() -> {
            Blackhole.consumeCPU(TOKENS);
            return null;
        });
    }
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ReadWriteLock.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
