package utils.concurrent.ArrayList;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.utils.concurrent.ConcurrentArrayList;

import java.util.ArrayList;
import java.util.Collections;
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

        @Benchmark public int modifyABArrayList() { return modTestList(); }

        @Benchmark public int modifyArrayList() { return modArrayList(); }
    }

    private static class ListsCombine {

        final List<Integer> arrayList = Collections.synchronizedList(new ArrayList<>());
        final ConcurrentArrayList<Integer> cntArrayList = new ConcurrentArrayList<>();


        public ListsCombine() {
            for (int i = 0; i < 10; ++i) {
                cntArrayList.add(i); arrayList.add(i);
            }
        }

        public int modTestList() {
            int n = ThreadLocalRandom.current().nextInt();
            cntArrayList.add(n);
            cntArrayList.remove(0);
            return n;
        }

        public int modArrayList() {
            int n = ThreadLocalRandom.current().nextInt();
            arrayList.add(n);
            arrayList.remove(0);
            return n;
        }
    }
}
