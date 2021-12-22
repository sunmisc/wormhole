package zelva.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class LoopRunner implements Runnable {
    private boolean run = true;
    private final Runnable source;

    public LoopRunner(Runnable source) {
        this.source = source;
    }

    @Override
    public void run() {
        while ((boolean) RN.getOpaque(this)) {
            source.run();
        }
    }

    public void stop() {
        RN.setOpaque(this, false);
    }

    private static final VarHandle RN;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            RN = l.findVarHandle(LoopRunner.class, "run", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}