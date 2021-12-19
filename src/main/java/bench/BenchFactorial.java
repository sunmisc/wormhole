package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/*
RESULT:============================================================
Benchmark              Mode  Cnt         Score         Error  Units
BenchFactorial.testFast    thrpt    4   8923229,182 ±  858874,974  ops/s
BenchFactorial.testSimple  thrpt    4  14978997,503 ± 1813955,950  ops/s
===================================================================
 */
@State(Scope.Thread)
public class BenchFactorial {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchFactorial.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(true)
                .build();
        new Runner(opt).run();
    }

    @Benchmark public long testFast()   { return factorialFast(99);   }
    @Benchmark public long testSimple() { return factorialSimple(99); }

    static long factorialFast(long n) {
        return recfact(1, n);
    }
    private static long recfact(long start, long n) {
        long i;
        if (n <= 16) {
            long r = start;
            for (i = start + 1; i < start + n; i++)
                r *= i;
            return r;
        }
        i = n >> 1; // division 2
        return recfact(start, i) * recfact(start + i, n - i);
    }

    static long factorialSimple(int n) {
        long f = 1;
        for (int i = 2; i <= n; i++)
            f *= i;
        return f;
    }
}
