package sunmisc.utils.concurrent.sched;

import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class CraftTask<V>
        extends FutureTask<V>
        implements RunnableScheduledFuture<V> {

    private volatile long delay;
    private final long id;

    public CraftTask(Callable<V> callable, long delay, long id) {
        super(callable);
        this.delay = delay;
        this.id = id;
    }

    public CraftTask(Runnable runnable, V result, long delay, long id) {
        super(runnable, result);
        this.delay = delay;
        this.id = id;
    }

    @Override
    public int compareTo(Delayed other) {
        if (other == this) // compare zero if same object
            return 0;
        int value = Long.compare(getDelay(NANOSECONDS), other.getDelay(NANOSECONDS));
        if (other instanceof CraftTask<?> task) {
            return value != 0 ? value : Long.compare(id, task.id);
        }
        return value;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(
                (delay - CraftScheduler.currentTick()) * 50,
                MILLISECONDS);
    }
    @Override
    public boolean isPeriodic() {
        return false; // todo:L
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        delay = 0;
        return super.cancel(mayInterruptIfRunning);
    }
}
