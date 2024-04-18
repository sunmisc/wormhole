package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.maps.ConcurrentEnumMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(2)
@Fork(1)
public class ThreadSafeMaps {

    @Param({"A", "N", "Z"})
    private Letter key;

    private @Param MapType mapType;
    private Map<Letter, String> map, mismatch;

    public enum MapType { HASH, ENUM }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ThreadSafeMaps.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void prepare() {
        Map<Letter, String> m = switch (mapType) {
            case HASH -> new ConcurrentHashMap<>();
            case ENUM -> new ConcurrentEnumMap<>(Letter.class);
        };
        Map<Letter, String> mm = new HashMap<>();
        for (final Letter x : Letter.values()) {
            m.put(x, x.name());
            if (ThreadLocalRandom.current().nextBoolean())
                mm.put(x, x.name());
        }
        map = m;
        mismatch = Map.copyOf(mm);
    }

    @Benchmark
    public Map.Entry<Letter, String> iterator() {
        Iterator<Map.Entry<Letter, String>> iterator
                = map.entrySet().iterator();
        Map.Entry<Letter, String> last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }

    public @Benchmark String putIfAbsent() {
        return map.putIfAbsent(key, "Test-Fest");
    }

    public @Benchmark String put() {
        return map.put(key, "Test-Fest");
    }

    public @Benchmark String remove() {
        return map.remove(key);
    }

    public @Benchmark boolean removeVal() {
        return map.remove(key, "T");
    }

    public @Benchmark boolean replace() {
        return map.replace(key, "Q", "L");
    }

    public @Benchmark String merge() {
        return map.merge(key, "Test-Fest", (k,v) -> "T");
    }

    public @Benchmark String compute() {
        return map.compute(key, (k,v) -> "F");
    }

    public @Benchmark String computeIfAbsent() {
        return map.computeIfAbsent(key, (k) -> "Q");
    }

    public @Benchmark String computeIfPresent() {
        return map.computeIfPresent(key, (k,v) -> "H");
    }

    public @Benchmark String get() {
        return map.get(key);
    }

    public @Benchmark int clear() {
        map.clear(); return 0;
    }

    public @Benchmark int mapHashCode() {
        return map.hashCode();}


    public @Benchmark boolean mapEquals() {
        return map.equals(mismatch);
    }

    public enum Letter {
        A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z
    }
}