package sunmisc.utils.concurrent;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.MathUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class UnblockingResizeArray {
    static final double SC = Math.PI * 5;
    static final int DELTA = 700_000;
    private UnblockingArrayBuffer<Integer> nextGen;
    private UnblockingArrayBufferOld<Integer> pastGen;

    private AtomicInteger a, b;


    @Setup
    public void prepare() {
        nextGen = new UnblockingArrayBuffer<>(DELTA);
        pastGen = new UnblockingArrayBufferOld<>(DELTA);
        a = new AtomicInteger(1);
        b = new AtomicInteger(1);

        for (int i = 0; i < DELTA; ++i) {
            nextGen.put(i,i);
            pastGen.put(i,i);
        }
    }

    @Benchmark
    public int resizeNextGen() {
        int x = getNextSize(a.getAndIncrement());
        nextGen.resize(r -> x);
        return x;
    }
    @Benchmark
    public int resizePastGen() {
        int x = getNextSize(b.getAndIncrement());
        pastGen.resize(r -> x);
        return x;
    }
    static int getNextSize(int a) {
        return (int) ((MathUtils._cos(a/SC) + 1) * DELTA);
    }
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(UnblockingResizeArray.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
