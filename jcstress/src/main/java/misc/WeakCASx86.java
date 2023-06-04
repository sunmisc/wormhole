package misc;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
@JCStressTest
@Outcome(id = "0", expect = Expect.FORBIDDEN, desc = "spurious failure")
@Outcome(id = "1", expect = Expect.ACCEPTABLE, desc = "stored")
@State
public class WeakCASx86 {
    private final Object lock = new Object();

    volatile Location loc = new Location(0,0, 0);


    public boolean weakCasFail() {
        synchronized (lock) {
            Location p = loc;
            return LOC.weakCompareAndSet(this, p,
                    new Location(p.x + 1, p.y + 1, p.z + 1));
        }
    }

    public boolean strongCasFail() {
        synchronized (this) {
            Location p = loc;
            return LOC.compareAndSet(this, p,
                    new Location(p.x + 1, p.y + 1, p.z + 1));
        }
    }

    @Actor
    public void actor1(L_Result r) {
        r.r1 = weakCasFail() ? 1 : 0;
    }

    @Actor
    public void actor2(L_Result r) {
        r.r1 = weakCasFail() ? 1 : 0;
    }

    private static final VarHandle LOC;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            LOC = l.findVarHandle(WeakCASx86.class,
                    "loc", Location.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static class Location {
        int x, y, z;

        Location(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
