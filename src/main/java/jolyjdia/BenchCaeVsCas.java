package jolyjdia;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/* WTF???
Benchmark           Mode  Cnt           Score          Error  Units
BenchCaeVsCas.cae  thrpt   25    51788738,954 ±  4603153,925  ops/s
BenchCaeVsCas.cas  thrpt   25  1221298925,434 ± 23802252,623  ops/s
*/

@Threads(6)
@State(Scope.Benchmark)
public class BenchCaeVsCas {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchCaeVsCas.class.getSimpleName())
                .syncIterations(false)
                .build();
        new Runner(opt).run();
    }

    private volatile String lazy;
    @Benchmark
    public String cas() { // witness
        // weakCas?
        for (String x;;) {
            if ((x = lazy) != null) {
                return x;
            } else if (VAL.compareAndSet(this, null, "Test-Fest")) {
                return null;
            }
        }
    }
    @Benchmark
    public String cae() {
        return (String) VAL.compareAndExchange(this, null, "Test-Fest");
    }
    private static final VarHandle VAL;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(BenchCaeVsCas.class, "lazy", String.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
