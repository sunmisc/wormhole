package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.concurrent.ConcurrentArrayList;
import zelva.concurrent.ConcurrentArrayListCHM;

import java.util.ArrayList;

//@Threads(4)
@State(Scope.Thread)
public class BenchArrList {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchArrList.class.getSimpleName())
                .forks(1)
                .measurementIterations(4)
                .operationsPerInvocation(100)
                .syncIterations(true)
                .build();
        new Runner(opt).run();
    }
    private ArrayList<String> govno;
    private ConcurrentArrayListCHM<String> chm;
   // private ConcurrentLinkedQueue<String> linkedQueue;

    @Setup
    public void prepare() {
        govno = new ArrayList<>();
       // chm = new ConcurrentArrayListCHM<>();
     //   linkedQueue = new ConcurrentLinkedQueue<>();
    }

    @Benchmark
    public Object addInGovno() {
        return govno.add("TestFest");
    }
    /*@Benchmark
    public Object addInСHM() {
        return chm.add("TestFest");
    }*/

    /*@Benchmark
    public Object addCLQ() {
        return linkedQueue.add("TestFest");
    }*/
}
