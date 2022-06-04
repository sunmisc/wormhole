package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.concurrent.ConcurrentCells;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Thread)
public class BenchCells {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchCells.class.getSimpleName())
                .measurementIterations(3)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

    static final int MAX_NODES = 10_000;
    final ConcurrentCells cells = new ConcurrentCells();
    final Map<Integer, Object> map = new ConcurrentHashMap<>();

    @Setup
    public void prepare() {
        for (int i = 0; i < MAX_NODES; ++i) {
            cells.set(i,i);
            map.put(i,i);
        }
    }
    @Benchmark
    public Object lookupCells() {
        return cells.get(MAX_NODES-1);
    }
    @Benchmark
    public Object lookupHashMap() {
        return map.get(MAX_NODES-1);
    }
    @Benchmark
    public Object setCells() {
        int r = ThreadLocalRandom.current().nextInt(10_000);
        cells.set(r, 2);
        return r;
    }
    @Benchmark
    public Object setHashMap() {
        return map.put(ThreadLocalRandom.current().nextInt(10_000), 2);
    }


}
