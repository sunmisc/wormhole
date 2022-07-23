package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.MathUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BenchConcurrencyMap {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchConcurrencyMap.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }

    @State(Scope.Thread)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public static class _1Thread extends ConcurrentMaps {
        @Benchmark
        public int putChm() {
            int i = getNextIndex(a.getAndIncrement());
            chm.put(i,i);
            return i;
        }
        @Benchmark
        public int putHm() {
            int i = getNextIndex(b.getAndIncrement());
            hm.put(i,i);
            return i;
        }
    }
    @State(Scope.Benchmark)
    @Threads(8)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public static class _8Threads extends ConcurrentMaps {
        @Benchmark
        public int putChm() {
            int i = getNextIndex(a.getAndIncrement());
            chm.put(i,i);
            return i;
        }
        @Benchmark
        public int putHm() {
            int i = getNextIndex(b.getAndIncrement());
            synchronized (hm) {
                hm.put(i,i);
            }
            return i;
        }
    }
    static class ConcurrentMaps {
        static final double SC = Math.PI * 5;
        final AtomicInteger a = new AtomicInteger();
        final ConcurrentMap<Integer,Integer> chm = new ConcurrentHashMap<>();
        final AtomicInteger b = new AtomicInteger();
        final Map<Integer,Integer> hm = new HashMap<>();

        static int getNextIndex(int a) {
            return (int) ((MathUtils._cos(a/SC) + 1) * 10_000);
        }

    }

}
