package bench;

import flempton.utils.concurrent.ConcurrentEnumMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

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

    @Param("U")
    private Letter key;

    final Map<Letter, String> hashMap = new ConcurrentHashMap<>();
    final Map<Letter,String> enumMap = new ConcurrentEnumMap<>(Letter.class);

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
}