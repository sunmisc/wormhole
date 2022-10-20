package concurrent.ArrayCells;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.MathUtils;
import zelva.utils.concurrent.ConcurrentArrayCells;
import zelva.utils.concurrent.ConcurrentCells;
import zelva.utils.concurrent.LockArrayCells;

import java.util.concurrent.atomic.AtomicInteger;

public class BenchResizeArray {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchResizeArray.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
    @Threads(Threads.MAX)
    @State(Scope.Benchmark)
    public static class MaxWorkers extends ConcurrentArrays {

        @Benchmark public int resizeConcurrent() { return rzConcurrent(); }

        @Benchmark public int resizeSynchronize() { return rzSynchronize(); }
    }


    @State(Scope.Thread)
    public static class _1Worker extends ConcurrentArrays {

        @Benchmark public int resizeConcurrent() { return rzConcurrent(); }

        @Benchmark public int resizeSynchronize() { return rzSynchronize(); }
    }

    private static class ConcurrentArrays {

        static final double SC = Math.PI * 5;
        static final int SZ = 700_000;

        private final AtomicInteger a = new AtomicInteger();
        private final AtomicInteger b = new AtomicInteger();

        private final ConcurrentArrayCells<Integer> cac
                = new ConcurrentArrayCells<>(1_000_000);
        private final ConcurrentCells<Integer> bac
                = new LockArrayCells<>(1_000_000);


        public int rzConcurrent() {
            int n = getNextSize(a.getAndIncrement());
            cac.resize(x -> n);
            return n;
        }

        public int rzSynchronize() {
            int n = getNextSize(b.getAndIncrement());
            bac.resize(x -> n);
            return n;
        }

        static int getNextSize(int a) {
            return (int) ((MathUtils._cos(a/SC) + 1) * SZ);
        }
    }
}
