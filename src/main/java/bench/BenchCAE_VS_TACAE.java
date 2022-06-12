package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.concurrent.AtomicRef;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/* false cae
Benchmark            Mode  Cnt           Score          Error  Units
BenchCaeVsCas.cae   thrpt   25    51436065,789 ±  4435443,091  ops/s
BenchCaeVsCas.ttas  thrpt   25  1044049933,794 ± 52089863,800  ops/s
*/

/* true cae
Benchmark                Mode  Cnt         Score         Error  Units
BenchCAE_VS_TACAE.cae   thrpt    4  54426063,046 ± 90287,771  ops/s
BenchCAE_VS_TACAE.ttas  thrpt    4  40377975,642 ± 59965,914  ops/s
 */

@Threads(6)
@State(Scope.Benchmark)
public class BenchCAE_VS_TACAE {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchCAE_VS_TACAE.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
    static final String VAl = "Test-Fest";

    volatile String lazy;
    final AtomicRef<String> ref = new AtomicRef<>(VAl);

    @Setup
    public void prepare() {
        lazy = VAl;
    }

    @Benchmark
    public String ttas() { // witness
        return ref.testAndCompareAndExchange(VAl, VAl);
    }
    @Benchmark
    public String cae() {
        return (String) VAL.compareAndExchange(this, VAl, VAl);
    }
    private static final VarHandle VAL;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VAL = l.findVarHandle(BenchCAE_VS_TACAE.class, "lazy", String.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
