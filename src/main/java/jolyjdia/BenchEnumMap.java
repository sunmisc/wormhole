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
BenchEnumMap.clearAsEnum                 Z  thrpt    4  3239249942,528 ± 281957737,117  ops/s
BenchEnumMap.clearAsHash                 Z  thrpt    4   252518722,893 ±   5042680,868  ops/s
BenchEnumMap.computeAsEnum               Z  thrpt    4    38032901,146 ±    149339,782  ops/s
BenchEnumMap.computeAsHash               Z  thrpt    4    15459624,543 ±     43466,130  ops/s
BenchEnumMap.computeIfAbsentAsEnum       Z  thrpt    4   923546063,859 ±  34907228,719  ops/s
BenchEnumMap.computeIfAbsentAsHash       Z  thrpt    4   341128986,026 ±   5445687,070  ops/s
BenchEnumMap.computeIfPresentAsEnum      Z  thrpt    4    39709575,109 ±    100499,171  ops/s
BenchEnumMap.computeIfPresentAsHash      Z  thrpt    4    15333622,885 ±     16163,984  ops/s
BenchEnumMap.getAsEnum                   Z  thrpt    4   863661327,675 ±  23646823,252  ops/s
BenchEnumMap.getAsHash                   Z  thrpt    4   463826903,556 ±  23080500,425  ops/s
BenchEnumMap.mergeAsEnum                 Z  thrpt    4    38826371,651 ±    132353,909  ops/s
BenchEnumMap.mergeAsHash                 Z  thrpt    4    15146730,000 ±     64064,033  ops/s
BenchEnumMap.putAsEnum                   Z  thrpt    4    54347348,568 ±    115558,776  ops/s
BenchEnumMap.putAsHash                   Z  thrpt    4    14805282,196 ±    250563,705  ops/s
BenchEnumMap.putIfAbsentAsEnum           Z  thrpt    4   910119993,420 ±   6692421,508  ops/s
BenchEnumMap.putIfAbsentAsHash           Z  thrpt    4   260719066,087 ±   1390688,013  ops/s
BenchEnumMap.removeAsEnum                Z  thrpt    4   909635947,993 ±  30169207,358  ops/s
BenchEnumMap.removeAsHash                Z  thrpt    4   466590443,964 ±  12343995,045  ops/s
BenchEnumMap.removeValAsEnum             Z  thrpt    4   978397821,638 ±  26025564,419  ops/s
BenchEnumMap.removeValAsHash             Z  thrpt    4    14800984,344 ±     67033,218  ops/s
BenchEnumMap.replaceAsEnum               Z  thrpt    4    53803738,660 ±    524848,001  ops/s
BenchEnumMap.replaceAsHash               Z  thrpt    4    14844581,365 ±     55100,219  ops/s
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
        hashMap.put(Letter.Z, "d"); // init table
        enumMap.put(Letter.Z, "d");
    }


    /*@Benchmark public String putIfAbsentAsHash() {return hashMap.putIfAbsent(key, "Test-Fest");}
    @Benchmark public String putIfAbsentAsEnum() {return enumMap.putIfAbsent(key, "Test-Fest");}

    @Benchmark public String putAsHash() {return hashMap.put(key, "Test-Fest");}
    @Benchmark public String putAsEnum() {return enumMap.put(key, "Test-Fest");}

    @Benchmark public String removeAsHash() {return hashMap.remove(key);}
    @Benchmark public String removeAsEnum() {return enumMap.remove(key);}*/

    @Benchmark public boolean removeValAsHash() {return hashMap.remove(key, "T");}
    @Benchmark public boolean removeValAsEnum() {return enumMap.remove(key, "T");}

    /*@Benchmark public boolean replaceAsHash() {return hashMap.replace(key, "Q", "L");}
    @Benchmark public boolean replaceAsEnum() {return enumMap.replace(key, "Q", "L");}

    @Benchmark public String mergeAsHash() {return hashMap.merge(key, "Test-Fest", (k,v) -> "T");}
    @Benchmark public String mergeAsEnum() {return enumMap.merge(key, "Test-Fest", (k,v) -> "T");}

    @Benchmark public String computeAsHash() {return hashMap.compute(key, (k,v) -> "F");}
    @Benchmark public String computeAsEnum() {return enumMap.compute(key, (k,v) -> "F");}

    @Benchmark public String computeIfAbsentAsHash() {return hashMap.computeIfAbsent(key, (k) -> "Q");}
    @Benchmark public String computeIfAbsentAsEnum() {return enumMap.computeIfAbsent(key, (k) -> "Q");}

    @Benchmark public String computeIfPresentAsHash() {return hashMap.computeIfPresent(key, (k,v) -> "H");}
    @Benchmark public String computeIfPresentAsEnum() {return enumMap.computeIfPresent(key, (k,v) -> "H");}

    @Benchmark public String getAsHash() {return hashMap.get(key);}
    @Benchmark public String getAsEnum() {return enumMap.get(key);}

    @Benchmark public void clearAsHash() {hashMap.clear();}
    @Benchmark public void clearAsEnum() {enumMap.clear();}*/


    public enum Letter {
        A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z
    }
}