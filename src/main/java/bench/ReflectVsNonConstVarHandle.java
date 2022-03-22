package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

@State(Scope.Thread)
public class ReflectVsNonConstVarHandle {

    public static void main(String[] args) throws RunnerException, IllegalAccessException {
        Options opt = new OptionsBuilder()
                .include(ReflectVsNonConstVarHandle.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .syncIterations(true)
                .build();
        new Runner(opt).run();
    }
    private TestAccess testAccess;

    private Field field;
    private VarHandle TVH;

    @Setup
    public void prepare() {
        testAccess = new TestAccess();
        testAccess.x20 = 20;
        try {
            field = TestAccess.class.getDeclaredField("x20");
            MethodHandles.Lookup l = MethodHandles.lookup();
            TVH = l.findVarHandle(TestAccess.class, "x20", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Benchmark
    public Object varHandleGet() {
        return TVH.get(testAccess);
    }
    @Benchmark
    public Object reflectGet() {
        try {
            return field.get(testAccess);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
    @Benchmark
    public Object varHandleSet() {
        int val = 13;
        TVH.set(testAccess, val);
        return val;
    }
    @Benchmark
    public Object reflectSet() {
        int val = 42;
        try {
            field.set(testAccess, val);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return val;
    }

    public static class TestAccess {
        private int x20;
    }
}
