package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.concurrent.ConcurrentArrayCopy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    final Set<Integer> chm = ConcurrentHashMap.newKeySet(MAX_NODES);
    final ConcurrentArrayCopy<Integer> cac = new ConcurrentArrayCopy<>(MAX_NODES);

    @Setup
    public void prepare() {
        for (int i = 0; i < MAX_NODES; ++i) {
            chm.add(i);
            cac.set(i,i);
        }
    }
    @Benchmark
    public boolean readCHM() {
        return chm.contains(MAX_NODES-1);
    }
    @Benchmark
    public boolean readCAC() {
        return cac.get(MAX_NODES-1) != null;
    }
    @Benchmark
    public Object writeCHM() {
        return chm.add(4);
    }
    @Benchmark
    public Object writeCAC() {
        return cac.set(4, 2);
    }
}
