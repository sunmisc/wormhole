package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.maps.ConcurrentLinkedHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(1)
public class ULinkedHashMap {

    @Benchmark
    @Group("linked")
    public Long put(ConcurrentLinkedHashMapState state) {
        long r = ThreadLocalRandom.current().nextLong();
        String v = String.valueOf(r);
        state.last = v;
        return state.map.put(v,r);
    }
    @Benchmark
    @Group("linked")
    public Long remove(ConcurrentLinkedHashMapState state) {
        return state.map.remove(state.last);
    }


    @Benchmark
    @Group("baseline")
    public Long put(ConcurrentHashMapState state) {
        long r = ThreadLocalRandom.current().nextLong();
        String v = String.valueOf(r);
        state.last = v;
        return state.map.put(v,r);
    }
    @Benchmark
    @Group("baseline")
    public Long remove(ConcurrentHashMapState state) {
        return state.map.remove(state.last);
    }

    @State(Scope.Group)
    public static class ConcurrentLinkedHashMapState extends AState{
        ConcurrentLinkedHashMap<String, Long> map;
        @Setup
        public void prepare() {
            ConcurrentLinkedHashMap<String,Long> map
                    = ConcurrentLinkedHashMap.newConcurrentLinkedHashMap();

            for (int i = 0; i < 1024; ++i)
                map.put(String.valueOf(i), (long)i);
            this.map = map;
        }
    }
    @State(Scope.Group)
    public static class ConcurrentHashMapState extends AState{
        ConcurrentHashMap<String,Long> map;
        @Setup
        public void prepare() {
            ConcurrentHashMap<String,Long> map
                    = new ConcurrentHashMap<>();

            for (int i = 0; i < 1024; ++i)
                map.put(String.valueOf(i), (long)i);
            this.map = map;
        }
    }

    private static class AState {
        volatile String last = "e";
    }
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ULinkedHashMap.class.getSimpleName())
                //.addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
