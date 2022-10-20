package zelva;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.CntArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BenchArrayList {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchArrayList.class.getSimpleName())
                .warmupIterations(2)
                .forks(1)
                .build();
        new Runner(opt).run();
    }
    @State(Scope.Thread)
    public static class _1Worker extends ListsCombine {

        @Benchmark public int modifyTestArrayList() { return modTestList(); }

        @Benchmark public int modifyArrayList() { return modJavaList(); }
    }

    private static class ListsCombine {

        final List<Integer> arrayList = new ArrayList<>();
        final CntArrayList<Integer> cntArrayList = new CntArrayList<>();


        public ListsCombine() {
            for (int i = 0; i < 1000; ++i) {
                cntArrayList.add(i); arrayList.add(i);
            }
        }

        public int modTestList() {
            int n = ThreadLocalRandom.current().nextInt();
            cntArrayList.add(n);
            cntArrayList.remove(0);
            return n;
        }

        public int modJavaList() {
            int n = ThreadLocalRandom.current().nextInt();
            arrayList.add(n);
            arrayList.remove(0);
            return n;
        }
    }
}
