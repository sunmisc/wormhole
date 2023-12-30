package sunmisc.utils.concurrent;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sunmisc.utils.concurrent.lists.ConcurrentArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class CArrayList {


    @Threads(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 2, time = 1)
    @Measurement(iterations = 4, time = 1)
    @Fork(1)
    @State(Scope.Benchmark)
    public static class SingleThread extends Bench {

        @Benchmark
        public int modifyToConcurrentList() {
            return super.modifyToConcurrentList();
        }
        @Benchmark
        public int modifyToCOWList() {
            return super.modifyToCOWList();
        }
        @Benchmark
        public int modifyToSynchronousList() {
            return super.modifyToSynchronousList();
        }

        @Benchmark
        public Integer readAsConcurrentList() {
            return super.readAsConcurrentList();
        }
        @Benchmark
        public Integer readAsCOWList() {
            return super.readAsCOWList();
        }
        @Benchmark
        public Integer readAsSynchronousList() {
            return super.readAsSynchronousList();
        }
        @Benchmark
        public boolean containsAsConcurrentList() {
            return super.containsAsConcurrentList();
        }
        @Benchmark
        public boolean containsAsCOWList() {
            return super.containsAsCOWList();
        }
        @Benchmark
        public boolean containsAsSynchronousList() {
            return super.containsAsSynchronousList();
        }
        @Setup
        public void prepare() {
            super.prepare();
        }
    }
    @Threads(Threads.MAX)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 2, time = 1)
    @Measurement(iterations = 4, time = 1)
    @Fork(1)
    @State(Scope.Benchmark)
    public static class ContendedBench extends Bench {

        @Benchmark
        public int modifyToConcurrentList() {
            return super.modifyToConcurrentList();
        }
        @Benchmark
        public int modifyToCOWList() {
            return super.modifyToCOWList();
        }
        @Benchmark
        public int modifyToSynchronousList() {
            return super.modifyToSynchronousList();
        }

        @Benchmark
        public Integer readAsConcurrentList() {
            return super.readAsConcurrentList();
        }
        @Benchmark
        public Integer readAsCOWList() {
            return super.readAsCOWList();
        }
        @Benchmark
        public Integer readAsSynchronousList() {
            return super.readAsSynchronousList();
        }

        @Benchmark
        public boolean containsAsConcurrentList() {
            return super.containsAsConcurrentList();
        }
        @Benchmark
        public boolean containsAsCOWList() {
            return super.containsAsCOWList();
        }
        @Benchmark
        public boolean containsAsSynchronousList() {
            return super.containsAsSynchronousList();
        }
        @Setup
        public void prepare() {
            super.prepare();
        }
    }

    public static class Bench {

        private static final List<Integer> IMMUTABLE =
                new ArrayList<>(); {

                    for (int i = 0; i < 100; ++i) {
                        IMMUTABLE.add(i);
                    }
        }
        List<Integer> optimisticList, cow, synchronous;

        public int modifyToConcurrentList() {
            int r = ThreadLocalRandom.current().nextInt();
            optimisticList.add(r);
            optimisticList.remove((Integer) r);
            return r;
        }
        public int modifyToCOWList() {
            int r = ThreadLocalRandom.current().nextInt();
            cow.add(r);
            cow.remove((Integer) r);
            return r;
        }
        public int modifyToSynchronousList() {
            int r = ThreadLocalRandom.current().nextInt();
            synchronous.add(r);
            synchronous.remove((Integer) r);
            return r;
        }

        public Integer readAsConcurrentList() {
            return optimisticList.get(0);
        }

        public Integer readAsCOWList() {
            return cow.get(0);
        }
        public Integer readAsSynchronousList() {
            return synchronous.get(0);
        }


        public boolean containsAsConcurrentList() {
            return optimisticList.containsAll(IMMUTABLE);
        }

        public boolean containsAsCOWList() {
            return cow.containsAll(IMMUTABLE);
        }
        public boolean containsAsSynchronousList() {
            return synchronous.containsAll(IMMUTABLE);
        }


        public void prepare() {
            optimisticList = new ConcurrentArrayList<>();
            cow = new CopyOnWriteArrayList<>();
            synchronous = Collections.synchronizedList(new ArrayList<>());

            optimisticList.add(1);
            cow.add(1);
            synchronous.add(1);
        }
    }

    public static void main(String[] args) throws RunnerException,
            CommandLineOptionException {
        Options opt = new OptionsBuilder()
                .include(CArrayList.class.getSimpleName())
                .build();
        Runner runner = new Runner(opt);
        runner.run();
    }

}
