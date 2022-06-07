package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.RunnerException;
import zelva.utils.concurrent.ConcurrentArrayCopy;
import zelva.utils.concurrent.ConcurrentCells;

@State(Scope.Thread)
public class BenchCells {

    public static void main(String[] args) throws RunnerException {
        ConcurrentArrayCopy<Integer> arrayCopy = new ConcurrentArrayCopy<>(12);
        System.out.println(arrayCopy.set(0, 1));
        System.out.println(arrayCopy.set(0, 1));

        arrayCopy.resize(13);
        /*Options opt = new OptionsBuilder()
                .include(BenchCells.class.getSimpleName())
                .measurementIterations(3)
                .forks(1)
                .build();
        new Runner(opt).run();*/
    }

    static final int MAX_NODES = 10_000;
    final ConcurrentCells cells = new ConcurrentCells();
    final ConcurrentArrayCopy<Integer> arrayCopy = new ConcurrentArrayCopy<>(MAX_NODES);

    @Setup
    public void prepare() {
        for (int i = 0; i < MAX_NODES; ++i) {
            //cells.set(i,i);
            arrayCopy.set(i,i);
        }
    }
    @Benchmark
    public Object lookupCells() {
        return cells.get(MAX_NODES-1);
    }
    @Benchmark
    public Object lookupCAC() {
        return arrayCopy.get(MAX_NODES-1);
    }
    @Benchmark
    public Object setHashMap() {
        return arrayCopy.set(4, 2);
    }
}
