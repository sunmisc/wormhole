package sunmisc.utils.concurrent.sched;

import java.time.Duration;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.*;

public final class DelayedScheduler extends Thread implements Scheduler {
    private final AtomicBoolean active = new AtomicBoolean();
    private final WorkQueue external = new HdTlWorkQueue();
    private final WorkQueue pending = new DelayedWorkQueue();
    private final Executor executor;

    public DelayedScheduler(final Executor executor) {
        this.executor = executor;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            external.dropAll(task -> {
                if (task.cancelled()) {
                    pending.remove(task);
                } else {
                    pending.push(task);
                }
            });
            pending.dropAll(executor::execute);
            if (active.get()) {
                active.compareAndSet(true, false);
            } else {
                final CancellationTask next = pending.peek();
                if (next == null) {
                    LockSupport.park();
                } else {
                    LockSupport.parkNanos(next.getDelay(NANOSECONDS));
                }
            }
        }
    }

    @Override
    public Cancellation schedule(Runnable action, Duration delay) {
        final CancellationTask task = new CancellationTask(
                this,
                action,
                delay.plusNanos(System.nanoTime())
        );
        final Thread current = Thread.currentThread();
        if (current == this) {
            pending.push(task);
        } else {
            external.push(task);
            signal();
        }
        return task;
    }

    private void cancel(final CancellationTask task) {
        final Thread current = Thread.currentThread();
        if (current == this) {
            pending.remove(task);
        } else {
            external.push(task);
            signal();
        }
    }

    private void signal() {
        if (!active.get() && active.compareAndSet(false, true)) {
            LockSupport.unpark(this);
        }
    }

    @Override
    public void close() {
        interrupt();
    }

    private final static class CancellationTask implements DelayedTask, Runnable {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final DelayedScheduler scheduler;
        private final Runnable action;
        private final Duration delay;

        public CancellationTask(final DelayedScheduler scheduler,
                                final Runnable action,
                                final Duration delay) {
            this.scheduler = scheduler;
            this.action = action;
            this.delay = delay;
        }

        @Override
        public boolean tryCancel() {
            if (!cancelled.getOpaque() && cancelled.compareAndSet(false, true)) {
                scheduler.cancel(this);
                return true;
            }
            return false;
        }

        public boolean cancelled() {
            return cancelled.get();
        }

        @Override
        public void run() {
            if (!cancelled.get()) {
                action.run();
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delay.toNanos() - System.nanoTime(), NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return o == this ? 0 :
                    Long.compare(
                            getDelay(TimeUnit.NANOSECONDS),
                            o.getDelay(TimeUnit.NANOSECONDS)
                    );
        }
    }

    private interface WorkQueue {

        boolean tryPush(CancellationTask task);

        void remove(CancellationTask task);

        CancellationTask peek();

        void dropAll(Consumer<CancellationTask> action);

        default void push(final CancellationTask task) {
            while (!this.tryPush(task));
        }
    }

    private final static class DelayedWorkQueue implements WorkQueue {
        private final Queue<CancellationTask> queue = new PriorityQueue<>();

        @Override
        public boolean tryPush(final CancellationTask task) {
            return this.queue.add(task);
        }

        @Override
        public void remove(CancellationTask task) {
            queue.remove(task);
        }

        @Override
        public CancellationTask peek() {
            return queue.peek();
        }

        @Override
        public void dropAll(final Consumer<CancellationTask> action) {
            for (CancellationTask h;
                 (h = this.queue.peek()) != null && h.getDelay(NANOSECONDS) <= 0;
                 action.accept(this.queue.poll())
            );
        }

        @Override
        public String toString() {
            return queue.toString();
        }
    }

    private static final class HdTlWorkQueue implements WorkQueue {
        private Node head;
        private final AtomicReference<Node> tail;

        public HdTlWorkQueue() {
            this.tail = new AtomicReference<>(this.head = new Node(null));
        }

        public HdTlWorkQueue(CancellationTask first) {
            final Node main = new Node(null);
            final Node task = new Node(first);
            main.link(task);
            this.head = main;
            this.tail = new AtomicReference<>(task);
        }

        @Override
        public void push(final CancellationTask task) {
            final Node node = new Node(task);
            final Node last = tail.getAndSet(node);
            last.link(node);
        }

        @Override
        public boolean tryPush(final CancellationTask task) {
            this.push(task);
            return true;
        }

        @Override
        public void remove(CancellationTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CancellationTask peek() {
            final Node node = head.next;
            return node == null ? null : node.task;
        }

        @Override
        public void dropAll(final Consumer<CancellationTask> action) {
            Node h = this.head;
            for (Node task = h.next; task != null; task = task.next) {
                action.accept(task.task);
                h = task;
            }
            h.unlink();
            this.head = h;
        }

        private static final class Node {
            private CancellationTask task;
            private volatile Node next;

            Node(CancellationTask task) {
                this.task = task;
            }

            void link(Node next) {
                this.next = next;
            }

            void unlink() {
                this.task = null;
            }
        }
    }
}
