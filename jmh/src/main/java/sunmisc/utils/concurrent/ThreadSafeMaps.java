package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.maps.ConcurrentEnumMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(ThreadSafeMaps.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void prepare() {
        final Map<Letter, String> m = switch (this.mapType) {
            case HASH -> new ConcurrentHashMap<>();
            case ENUM -> new ConcurrentEnumMap<>(Letter.class);
        };
        final Map<Letter, String> mm = new HashMap<>();
        for (final Letter x : Letter.values()) {
            m.put(x, x.name());
            if (ThreadLocalRandom.current().nextBoolean()) {
                mm.put(x, x.name());
            }
        }
        this.map = m;
        this.mismatch = Map.copyOf(mm);
    }

    @Benchmark
    public Map.Entry<Letter, String> iterator() {
        final Iterator<Map.Entry<Letter, String>> iterator
                = this.map.entrySet().iterator();
        Map.Entry<Letter, String> last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }

    public @Benchmark String putIfAbsent() {
        return this.map.putIfAbsent(this.key, "Test-Fest");
    }

    public @Benchmark String put() {
        return this.map.put(this.key, "Test-Fest");
    }

    public @Benchmark String remove() {
        return this.map.remove(this.key);
    }

    public @Benchmark boolean removeVal() {
        return this.map.remove(this.key, "T");
    }

    public @Benchmark boolean replace() {
        return this.map.replace(this.key, "Q", "L");
    }

    public @Benchmark String merge() {
        return this.map.merge(this.key, "Test-Fest", (k, v) -> "T");
    }

    public @Benchmark String compute() {
        return this.map.compute(this.key, (k, v) -> "F");
    }

    public @Benchmark String computeIfAbsent() {
        return this.map.computeIfAbsent(this.key, (k) -> "Q");
    }

    public @Benchmark String computeIfPresent() {
        return this.map.computeIfPresent(this.key, (k, v) -> "H");
    }

    public @Benchmark String get() {
        return this.map.get(this.key);
    }

    public @Benchmark int clear() {
        this.map.clear(); return 0;
    }

    public @Benchmark int mapHashCode() {
        return this.map.hashCode();}


    public @Benchmark boolean mapEquals() {
        return this.map.equals(this.mismatch);
    }

    public enum Letter {
        A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z
    }
}