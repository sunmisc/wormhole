package sunmisc.utils;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class ReferenceArrayCopy {

    private Cell[] prevDef, prevIntrinsic;

    @Setup
    public void prepare() {
        prevDef = new Cell[2];
        prevIntrinsic = new Cell[2];

        for (int i = 0; i < 2; ++i) {
            Cell x = new Cell(), y = new Cell();
            x.x = 15; y.x = 15;

            prevDef[i] = x;
            prevIntrinsic[i] = y;
        }
    }

    @Benchmark
    public int copyDefault() {
        final Cell[] cs = prevDef; int n = cs.length;

        final int newLen = Math.min(4096, n << 1);
        final Cell[] next = new Cell[newLen];
        for (int i = 0; i < n; i++) {
            Cell o = cs[i];
            if (o != null)
                next[i] = o;
        }
        prevDef = next;
        return newLen;
    }
    @Benchmark
    public int copyIntrinsic() {
        Cell[] cs = prevIntrinsic;
        int n = cs.length, newLen = Math.min(4096, n << 1);

        prevIntrinsic = Arrays.copyOf(cs, newLen);
        return newLen;
    }



    private static class Cell {
        volatile long x;
    }
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ReferenceArrayCopy.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
