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

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/*
Benchmark                              (key)   Mode  Cnt         Score        Error  Units
AtomicTransferArrayBench.getAndAtomic  thrpt    4  143325627,929 ± 28977962,401  ops/s
AtomicTransferArrayBench.getAndLock    thrpt    4   27359068,859 ±   978932,819  ops/s
AtomicTransferArrayBench.setAndAtomic  thrpt    4  130877180,373 ± 19527474,119  ops/s
AtomicTransferArrayBench.setAndLock    thrpt    4   26618863,474 ±  6900754,344  ops/s
 */
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

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArray<>(2);
        defArray = new Integer[2];
    }

    @Benchmark
    public Integer growAtomicArray() {
        int i = ThreadLocalRandom.current().nextInt(8);
        myArray.resize(i);
        return i;
    }
    /*@Benchmark
    public synchronized Integer growDefArray() {
        defArray = Arrays.copyOf(defArray, ThreadLocalRandom.current().nextInt(8));
        return defArray.length;
    }*/
}
