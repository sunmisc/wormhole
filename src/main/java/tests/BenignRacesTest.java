package tests;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

public class BenignRacesTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
    public static class NonFinalTest {
        TestObject instance;

        int getResult() {
            TestObject m = lazyGet();
            return (m != null) ?
                    (m.get() ? 1 : 5)
                    : -1;
        }

        TestObject lazyGet() {
            TestObject t = instance;
            if (t == null) {
                t = new TestObject();
                instance = t;
            }
            return t;
        }
    }

    @JCStressTest
    @Outcome(id = "1, 1", expect = ACCEPTABLE, desc = "Boring")
    @State
    public static class NonFinal extends NonFinalTest {
        @Actor
        public void actor1(II_Result r) {
            r.r1 = getResult();
        }

        @Actor
        public void actor2(II_Result r) {
            r.r2 = getResult();
        }
        @Actor
        public void eraser() {
            instance = null;
        }
    }
    public static class TestObject {
        boolean x = true;

        public boolean get() {
            return (boolean) VH.getOpaque(this);
        }

        private static final VarHandle VH;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VH = l.findVarHandle(TestObject.class, "x", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}
