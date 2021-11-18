package jolyjdia;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.concurrent.ConcurrentEnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Threads(6)
@State(Scope.Benchmark)
public class BenchEnumMap {
    private ConcurrentMap<Letter, String> hashMap;
    private ConcurrentEnumMap<Letter,String> enumMap;

    @Param({"Z"})
    private Letter key;

    /*
     * Benchmark                       (key)   Mode  Cnt          Score           Error  Units
     * BenchEnumMap.computeAsEnum          Z  thrpt    4   40249756,561 ±    679926,560  ops/s
     * BenchEnumMap.computeAsHash          Z  thrpt    4   16962649,227 ±   2869472,846  ops/s
     * BenchEnumMap.getAsEnum              Z  thrpt    4  893933370,725 ± 157271256,136  ops/s
     * BenchEnumMap.getAsHash              Z  thrpt    4  719249929,500 ± 189989971,137  ops/s
     * BenchEnumMap.mergeAsEnum            Z  thrpt    4   32432104,072 ±   2370180,085  ops/s
     * BenchEnumMap.mergeAsHash            Z  thrpt    4   16702588,323 ±    334932,015  ops/s
     * BenchEnumMap.putAsEnum              Z  thrpt    4  789612094,013 ± 201686815,813  ops/s
     * BenchEnumMap.putAsHash              Z  thrpt    4   89788211,319 ±  34289895,026  ops/s
     * BenchEnumMap.putIfAbsentAsEnum      Z  thrpt    4  773278008,272 ± 433393792,969  ops/s
     * BenchEnumMap.putIfAbsentAsHash      Z  thrpt    4  253907112,887 ±  43820648,054  ops/s
     * BenchEnumMap.removeAsEnum           Z  thrpt    4  893061185,061 ± 143731521,644  ops/s
     * BenchEnumMap.removeAsHash           Z  thrpt    4  518951031,713 ± 173961488,373  ops/s
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchEnumMap.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(false)
                .build();
        new Runner(opt).run();
    }
    @Setup
    public void prepare() {
        hashMap = new ConcurrentHashMap<>();
        enumMap = new ConcurrentEnumMap<>(Letter.class);
    }


/*    @Benchmark public String putIfAbsentAsHash() {return hashMap.putIfAbsent(key, "Test-Fest");}
    @Benchmark public String putIfAbsentAsEnum() {return enumMap.putIfAbsent(key, "Test-Fest");}*/

    @Benchmark public String putAsHash() {return hashMap.put(key, "Test-Fest");}
    @Benchmark public String putAsEnum() {return enumMap.put(key, "Test-Fest");}

    @Benchmark public String removeAsHash() {return hashMap.remove(key);}
    @Benchmark public String removeAsEnum() {return enumMap.remove(key);}

/*    @Benchmark public String mergeAsHash() {return hashMap.merge(key, "Test-Fest", (k,v) -> "T");}
    @Benchmark public String mergeAsEnum() {return enumMap.merge(key, "Test-Fest", (k,v) -> "T");}

    @Benchmark public String computeAsHash() {return hashMap.compute(key, (k,v) -> "F");}
    @Benchmark public String computeAsEnum() {return enumMap.compute(key, (k,v) -> "F");}

    @Benchmark public String getAsHash() {return hashMap.get(key);}
    @Benchmark public String getAsEnum() {return enumMap.get(key);}*/


    public enum Letter {
        A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z
    }
}