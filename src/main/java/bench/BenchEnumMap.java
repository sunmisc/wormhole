package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.concurrent.ConcurrentEnumMap;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@State(Scope.Benchmark)
public class BenchEnumMap {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchEnumMap.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    private Map<Letter, String> hashMap;
    private Map<Letter,String> enumMap;

    @Param("U")
    private Letter key;

    @Setup
    public void prepare() {
        hashMap = new ConcurrentHashMap<>();
        enumMap = new ConcurrentEnumMap<>(Letter.class);
        for (Letter l : Letter.values()) {
            hashMap.put(l, l.toString()); // init table
            enumMap.put(l, l.toString());
        }
    }
    @Benchmark
    public Map.Entry<Letter, String> enumMapIterator() {
        Iterator<Map.Entry<Letter, String>> iterator
                = enumMap.entrySet().iterator();
        Map.Entry<Letter, String> last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }
    @Benchmark
    public Map.Entry<Letter, String> hashMapIterator() {
        Iterator<Map.Entry<Letter, String>> iterator
                = hashMap.entrySet().iterator();
        Map.Entry<Letter, String> last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
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

    @Benchmark public int clearAsHash() {hashMap.clear(); return 0;}
    @Benchmark public int clearAsEnum() {enumMap.clear(); return 0;}

    @Benchmark public int hashCodeHashMap() {return hashMap.hashCode();}
    @Benchmark public int hashCodeEnumMap() {return enumMap.hashCode();}

    public enum Letter {
        A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z
    }
    /*
Benchmark                            (key)   Mode  Cnt           Score          Error  Units
BenchEnumMap.clearAsEnum                 U  thrpt   25  1684565858,025 ± 48338460,812  ops/s
BenchEnumMap.clearAsHash                 U  thrpt   25   127489353,028 ±  3131787,264  ops/s
BenchEnumMap.computeAsEnum               U  thrpt   25    34689377,057 ±  1806263,450  ops/s
BenchEnumMap.computeAsHash               U  thrpt   25    20486702,812 ±  4880777,546  ops/s
BenchEnumMap.computeIfAbsentAsEnum       U  thrpt   25  1083314169,607 ± 66665836,254  ops/s
BenchEnumMap.computeIfAbsentAsHash       U  thrpt   25   107502277,033 ± 63683693,185  ops/s
BenchEnumMap.computeIfPresentAsEnum      U  thrpt   25  1189788644,096 ± 41484304,277  ops/s
BenchEnumMap.computeIfPresentAsHash      U  thrpt   25   243902898,901 ± 72992321,249  ops/s
BenchEnumMap.getAsEnum                   U  thrpt   25  1202570594,607 ± 34709252,956  ops/s
BenchEnumMap.getAsHash                   U  thrpt   25   698349844,237 ±  5852891,447  ops/s
BenchEnumMap.hashCodeEnumMap             U  thrpt   25    45200463,952 ± 12206506,510  ops/s
BenchEnumMap.hashCodeHashMap             U  thrpt   25    55710232,462 ± 11431933,201  ops/s
BenchEnumMap.mergeAsEnum                 U  thrpt   25    37109448,818 ±   613758,458  ops/s
BenchEnumMap.mergeAsHash                 U  thrpt   25    20496422,089 ±  3939457,806  ops/s
BenchEnumMap.putAsEnum                   U  thrpt   25    52365556,626 ±  4444423,267  ops/s
BenchEnumMap.putAsHash                   U  thrpt   25    15114803,764 ±  2754930,848  ops/s
BenchEnumMap.putIfAbsentAsEnum           U  thrpt   25  1096425067,656 ± 28378588,866  ops/s
BenchEnumMap.putIfAbsentAsHash           U  thrpt   25   122415506,410 ± 40183092,216  ops/s
BenchEnumMap.removeAsEnum                U  thrpt   25  1152279540,882 ± 51121553,044  ops/s
BenchEnumMap.removeAsHash                U  thrpt   25   170387907,609 ± 44107568,919  ops/s
BenchEnumMap.removeValAsEnum             U  thrpt   25  1258469355,987 ± 33560087,243  ops/s
BenchEnumMap.removeValAsHash             U  thrpt   25   201538575,584 ±  3587739,653  ops/s
BenchEnumMap.replaceAsEnum               U  thrpt   25  1309242394,289 ± 30249610,603  ops/s
BenchEnumMap.replaceAsHash               U  thrpt   25   199651182,445 ±  4575851,093  ops/s
     */
}