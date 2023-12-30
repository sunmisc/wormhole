package sunmisc.utils;

import org.openjdk.jmh.annotations.*;
import sunmisc.utils.concurrent.ImmutableLinkedList;
import sunmisc.utils.math.FTrigonometry;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 12, time = 1)
@Threads(1)
@Fork(1)
public class MathUtilsSinCos {
    @Param({"121.545454", "0.322"})
    float val;
    final FTrigonometry trigonometry = new FTrigonometry();


    @Benchmark
    public float fastSin()   {
        return trigonometry.sin(val).floatValue();
    }
    @Benchmark
    public double javaSin() {
        return Math.sin(val);
    }
    public static void main(String[] args) {
        ImmutableLinkedList<Integer> origin =
                new ImmutableLinkedList<>(null, null, null);
        ImmutableLinkedList<Integer> queue = origin;
        for (int i = 0; i < 6; ++i) {
            queue = queue.addLast(i);//.append(i);
        }
        System.out.println(queue);
        queue = queue.pollLast();
        System.out.println(queue);
    }
    /*public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MathUtilsSinCos.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }*/
}
