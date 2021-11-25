package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.concurrent.AtomicTransferArray;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/*
AtomicTransferArrayBench.setAndResizeAtomic   2224  thrpt    4  19649842,037 ± 202856,969  ops/s
AtomicTransferArrayBench.setAndResizeLock     2224  thrpt    4   7477161,135 ± 510439,179  ops/s
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
    public Integer setAndResizeAtomic() {
        Integer i = myArray.set(key, 1);
        myArray.resize(ThreadLocalRandom.current().nextInt(2, 8));
        return i;
    }
    @Benchmark
    public Integer setAndResizeLock() {
        synchronized (defArray) {
            Integer i = defArray[1];
            defArray[1] = key;
            defArray = Arrays.copyOf(defArray, ThreadLocalRandom.current().nextInt(2, 8));
            return i;
        }
    }
}
