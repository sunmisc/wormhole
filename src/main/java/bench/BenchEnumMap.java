package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.concurrent.ConcurrentEnumMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Threads(4)
@State(Scope.Benchmark)
public class BenchEnumMap {
    private ConcurrentMap<Letter, String> hashMap;
    private ConcurrentEnumMap<Letter,String> enumMap;

    @Param({"Z"})
    private Letter key;

    /*
BenchEnumMap.clearAsEnum                 Z  thrpt    4  686395736,302 ± 230608979,419  ops/s
BenchEnumMap.clearAsHash                 Z  thrpt    4   60289261,444 ±  28201814,010  ops/s
BenchEnumMap.computeAsEnum               Z  thrpt    4   20650947,368 ±   3778496,004  ops/s
BenchEnumMap.computeAsHash               Z  thrpt    4    9420015,234 ±    617690,361  ops/s
BenchEnumMap.computeIfAbsentAsEnum       Z  thrpt    4  197213163,709 ±  54999107,567  ops/s
BenchEnumMap.computeIfAbsentAsHash       Z  thrpt    4   82471327,102 ±  49394770,034  ops/s
BenchEnumMap.computeIfPresentAsEnum      Z  thrpt    4   22698793,879 ±    708463,226  ops/s
BenchEnumMap.computeIfPresentAsHash      Z  thrpt    4    9473014,783 ±    259991,554  ops/s
BenchEnumMap.getAsEnum                   Z  thrpt    4  192121543,000 ±  23857496,861  ops/s
BenchEnumMap.getAsHash                   Z  thrpt    4  102144250,940 ± 101911570,816  ops/s
BenchEnumMap.mergeAsEnum                 Z  thrpt    4   20710255,272 ±   5140263,144  ops/s
BenchEnumMap.mergeAsHash                 Z  thrpt    4   10471602,776 ±   2028967,923  ops/s
BenchEnumMap.putAsEnum                   Z  thrpt    4   26630174,673 ±   6186430,487  ops/s
BenchEnumMap.putAsHash                   Z  thrpt    4   11838846,055 ±   4036207,895  ops/s
BenchEnumMap.putIfAbsentAsEnum           Z  thrpt    4  166765030,384 ±  49010635,486  ops/s
BenchEnumMap.putIfAbsentAsHash           Z  thrpt    4   71007820,881 ±   8870377,910  ops/s
BenchEnumMap.removeAsEnum                Z  thrpt    4  197855606,182 ±  38522830,182  ops/s
BenchEnumMap.removeAsHash                Z  thrpt    4   92593926,478 ±  59725832,578  ops/s
BenchEnumMap.removeValAsEnum             Z  thrpt    4  222894784,537 ±  92835536,783  ops/s
BenchEnumMap.removeValAsHash             Z  thrpt    4    8413816,697 ±    870552,493  ops/s
BenchEnumMap.replaceAsEnum               Z  thrpt    4  232100122,163 ± 187016597,788  ops/s
BenchEnumMap.replaceAsHash               Z  thrpt    4    9407603,460 ±    736172,444  ops/s
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


    @Benchmark public String putIfAbsentAsHash() {return hashMap.putIfAbsent(key, "Test-Fest");}
    @Benchmark public String putIfAbsentAsEnum() {return enumMap.putIfAbsent(key, "Test-Fest");}

    @Benchmark public String putAsHash() {return hashMap.put(key, "Test-Fest");}
    @Benchmark public String putAsEnum() {return enumMap.put(key, "Test-Fest");}

    @Benchmark public String removeAsHash() {return hashMap.remove(key);}
    @Benchmark public String removeAsEnum() {return enumMap.remove(key);}

    @Benchmark public boolean removeValAsHash() {return hashMap.remove(key, "T");}
    @Benchmark public boolean removeValAsEnum() {return enumMap.remove(key, "T");}

    @Benchmark public boolean replaceAsHash() {return hashMap.replace(key, "Q", "L");}
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
    @Benchmark public void clearAsEnum() {enumMap.clear();}


    public enum Letter {
        A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z
    }
}