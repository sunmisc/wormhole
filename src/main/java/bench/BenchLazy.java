package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.concurrent.Lazy;

import java.util.ArrayList;
import java.util.function.Supplier;

@Threads(6)
@State(Scope.Benchmark)
public class BenchLazy {
    private Lazy<ArrayList<String>> listLazyAcq;
    private LazyDoubleCheckedLocking<ArrayList<String>> listLazyLock;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchLazy.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(false)
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void prepare() {
        listLazyAcq = new Lazy<>(() -> {
            ArrayList<String> list = new ArrayList<>();
            list.add("Fest");
            return list;
        });
        listLazyLock = new LazyDoubleCheckedLocking<>(() -> {
            ArrayList<String> list = new ArrayList<>();
            list.add("Fest");
            return list;
        });
    }
    @Benchmark
    public ArrayList<String> listLazyLock() {
        return listLazyLock.getOrLoad();
    }
    @Benchmark
    public ArrayList<String> listLazyAcq() {
        return listLazyAcq.getOrLoad();
    }

    private static class LazyDoubleCheckedLocking<V> {
        private volatile V val;
        private final Supplier<V> supplier;

        public LazyDoubleCheckedLocking(Supplier<V> supplier) {
            this.supplier = supplier;
        }
        public V getOrLoad() {
            V v = val;
            if (v == null) {
                synchronized (this) {
                    if ((v = val) == null) {
                        return val = supplier.get();
                    }
                }
            }
            return v;
        }
    }
}
