package zelva.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class LoopRunner implements Runnable {
    private boolean stop;
    private final Runnable source;

    public LoopRunner(Runnable source) {
        this.source = source;
    }

    @Override
    public void run() {
        while ((boolean) STOP.getOpaque(this)) {
            source.run();
        }
    }

    public void stop() {
        STOP.setOpaque(this, false);
    }

    private static final VarHandle STOP;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STOP = l.findVarHandle(LoopRunner.class, "stop", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}