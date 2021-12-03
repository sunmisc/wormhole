package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.concurrent.AtomicTransferArray;

/*
Benchmark                              (key)   Mode  Cnt         Score        Error  Units
AtomicTransferArrayBench.getAndAtomic  thrpt    4  143325627,929 ± 28977962,401  ops/s
AtomicTransferArrayBench.getAndLock    thrpt    4   27359068,859 ±   978932,819  ops/s
AtomicTransferArrayBench.setAndAtomic  thrpt    4  130877180,373 ± 19527474,119  ops/s
AtomicTransferArrayBench.setAndLock    thrpt    4   26618863,474 ±  6900754,344  ops/s
 */
@State(Scope.Thread)
public class AtomicTransferArrayBench {
    private AtomicTransferArray<Integer> myArray;
    private Integer[] defArray;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicTransferArrayBench.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(true)
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArray<>(2);
        defArray = new Integer[2];
        myArray.set(1, 666);
        defArray[1] = 666;
    }

    @Benchmark
    public Integer getAndAtomic() {
        return myArray.get(1);
    }
    @Benchmark
    public Integer setAndAtomic() {
        return myArray.set(1, 5);
    }
    @Benchmark
    public Integer getAndLock() {
        return defArray[1];
    }
    @Benchmark
    public Integer setAndLock() {
        Integer i = defArray[1];
        defArray[1] = 5;
        return i;
    }
}
