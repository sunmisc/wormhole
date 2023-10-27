package sunmisc.utils.concurrent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class JoinerVsBuilder {

    private Cell[] cells;

    @Setup
    public void prepare() {
        cells = new Cell[1024];
        for (int i = 0; i < cells.length; ++i)
            cells[i] = new Cell(i);
    }
    @Benchmark
    public String stringJoiner() {
        StringJoiner joiner = new StringJoiner(
                ", ", "[", "]");
        for (Cell c : cells)
            joiner.add(c.toString());
        return joiner.toString();
    }
    @Benchmark
    public String stringBuilder() {
        return Arrays.toString(cells);
    }
    private record Cell(long value) {
        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JoinerVsBuilder.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
