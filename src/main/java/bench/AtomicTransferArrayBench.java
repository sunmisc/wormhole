package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import tests.AtomicTransferArrayTest;

import java.util.concurrent.ThreadLocalRandom;

/*
AtomicTransferArrayBench.growAtomicArray  thrpt    4  3777303,444 ± 5648846,663  ops/s
AtomicTransferArrayBench.growLockArray    thrpt    4  7080428,068 ± 5849378,647  ops/s


 */
@State(Scope.Benchmark)
public class AtomicTransferArrayBench {
    private AtomicTransferArrayTest.MyAtomicResizeArrayCopy myArray;
    private AtomicTransferArrayTest.LockResizeArray lockArray;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicTransferArrayBench.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .threads(8)
                .syncIterations(false)
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArrayTest.MyAtomicResizeArrayCopy();
        lockArray = new AtomicTransferArrayTest.LockResizeArray();
    }

    @Benchmark
    public Integer growAtomicArray() {
        int i = ThreadLocalRandom.current().nextInt(1, 8);
        myArray.resize(i);
        return i;
    }

    @Benchmark
    public Integer growLockArray() {
        int i = ThreadLocalRandom.current().nextInt(1, 8);
        lockArray.resize(i);
        return i;
    }
}
