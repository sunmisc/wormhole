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

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
public class BenchAtomicResizeArray {
    private AtomicTransferArrayTest.MyAtomicResizeArrayCopy myArray;
    volatile Integer[] array;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchAtomicResizeArray.class.getSimpleName())
                .syncIterations(false)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArrayTest.MyAtomicResizeArrayCopy();
        array = new Integer[2];
    }

    @Benchmark
    public Integer growAtomicArray() {
        int i = ThreadLocalRandom.current().nextInt(1, 12);
        myArray.resize(i);
        return i;
    }

    @Benchmark
    public Integer growArrayIntrinsic() {
        int i = ThreadLocalRandom.current().nextInt(1, 12);
        synchronized (this) {
            array = Arrays.copyOf(array, i);
        }
        return i;
    }
}
