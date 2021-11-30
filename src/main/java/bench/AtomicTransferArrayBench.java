package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.concurrent.AtomicTransferArray;

/*
Benchmark                              (key)   Mode  Cnt         Score        Error  Units
AtomicTransferArrayBench.setAndAtomic   2224  thrpt    4  24701540,852 ± 984372,111  ops/s
AtomicTransferArrayBench.setAndLock     2224  thrpt    4  10705102,252 ± 223066,806  ops/s
 */
@Threads(6)
@State(Scope.Benchmark)
public class AtomicTransferArrayBench {
    private AtomicTransferArray<Integer> myArray;
    private Integer[] defArray;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicTransferArrayBench.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(false)
                .build();
        new Runner(opt).run();
    }
    @Param({"2224"})
    private int key;

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArray<>(2);
        defArray = new Integer[2];
    }

    @Benchmark
    public Integer setAndAtomic() {
        return myArray.set(1, key);
    }
    @Benchmark
    public Integer setAndLock() {
        synchronized (defArray) {
            Integer i = defArray[1];
            defArray[1] = key;
            return i;
        }
    }
}
