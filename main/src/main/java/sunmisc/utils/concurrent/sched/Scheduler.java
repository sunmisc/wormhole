package sunmisc.utils.concurrent.sched;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public interface Scheduler extends Closeable {

    Cancellation schedule(final Runnable action, final Duration delay);

    default <V> Future<V> schedule(final Callable<V> callable, final Duration delay) {
        return new DelayFuture<>(callable, this, delay);
    }

    default Cancellation scheduleAtFixedRate(final Runnable runnable,
                                             final Duration delay,
                                             final Duration period) {
        final RecursiveScheduleTask task = new RecursiveScheduleTask(
                runnable,
                this,
                period
        );
        task.schedule(delay);
        return task;
    }

    final class RecursiveScheduleTask implements Cancellation {
        private final AtomicReference<Cancellation> current = new AtomicReference<>();
        private final AtomicBoolean cancel = new AtomicBoolean();
        private final Runnable action;
        private final Scheduler scheduler;
        private final Duration period;

        public RecursiveScheduleTask(final Runnable action,
                                     final Scheduler scheduler,
                                     final Duration period) {
            this.action = action;
            this.scheduler = scheduler;
            this.period = period;
        }

        public void schedule(final Duration delay) {
            if (!cancel.get()) {
                current.setRelease(
                        scheduler.schedule(() -> {
                            action.run();
                            schedule(period);
                        }, delay)
                );
            }
        }

        @Override
        public boolean tryCancel() {
            return !cancel.getOpaque() &&
                    cancel.compareAndSet(false, true) &&
                    current.getAcquire().tryCancel();
        }
    }

    final class DelayFuture<V> extends FutureTask<V> {
        private final Cancellation cancellation;

        public DelayFuture(final Callable<V> callable,
                           final Scheduler scheduler,
                           final Duration delay) {
            super(callable);
            this.cancellation = scheduler.schedule(this, delay);
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return this.cancellation.tryCancel() &&  super.cancel(mayInterruptIfRunning);
        }
    }
}
