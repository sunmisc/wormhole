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

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(ThreadSafeMaps.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Param({"A", "N", "Z"})
    private Letter key;
    private @Param MapType mapType;
    private Map<Letter, String> map, mismatch;
    public enum MapType { HASH, ENUM }
    public enum Letter {
        A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z
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

    @Benchmark
    public String putIfAbsent() {
        return this.map.putIfAbsent(this.key, "Test-Fest");
    }

    @Benchmark
    public String put() {
        return this.map.put(this.key, "Test-Fest");
    }

    @Benchmark
    public String remove() {
        return this.map.remove(this.key);
    }

    @Benchmark
    public boolean removeVal() {
        return this.map.remove(this.key, "T");
    }

    @Benchmark
    public boolean replace() {
        return this.map.replace(this.key, "Q", "L");
    }

    @Benchmark
    public String merge() {
        return this.map.merge(this.key, "Test-Fest", (k, v) -> "T");
    }

    @Benchmark
    public String compute() {
        return this.map.compute(this.key, (k, v) -> "F");
    }

    @Benchmark
    public String computeIfAbsent() {
        return this.map.computeIfAbsent(this.key, (k) -> "Q");
    }

    @Benchmark
    public String computeIfPresent() {
        return this.map.computeIfPresent(this.key, (k, v) -> "H");
    }

    @Benchmark
    public String get() {
        return this.map.get(this.key);
    }

    @Benchmark
    public int clear() {
        this.map.clear(); return 0;
    }

    @Benchmark
    public int mapHashCode() {
        return this.map.hashCode();}

    @Benchmark
    public boolean mapEquals() {
        return this.map.equals(this.mismatch);
    }
}