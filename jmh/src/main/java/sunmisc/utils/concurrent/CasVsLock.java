package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class CasVsLock {


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CasVsLock.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    private AtomicReference<TestObject> atomic;

    private ReentrantLock lock;
    private TestObject obj;

    @Setup
    public void prepare() {
        atomic = new AtomicReference<>();
        lock = new ReentrantLock();
        obj = new TestObject(12);
    }
    @Benchmark
    public boolean cas() {
        return atomic.compareAndSet(atomic.getPlain(), new TestObject(12));
    }

    @Benchmark
    public boolean lock() {
        TestObject o = obj;

        lock.lock();
        try {

            if (obj == o) {
                obj = new TestObject(12);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    private static class TestObject {
        int val;

        public TestObject(int val) {
            this.val = val;
        }
    }
}
