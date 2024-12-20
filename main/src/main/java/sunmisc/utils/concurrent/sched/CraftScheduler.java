package sunmisc.utils.concurrent.sched;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class CraftScheduler implements Runnable, AutoCloseable {
    private static volatile int tick;

    private final WorkQueue workQueue = new StripedQueue();
    private final ConcurrentMap<Long, CraftTask<?>> tasks =
            new ConcurrentHashMap<>();
    private final WorkQueue delayedQueue;

    private final AtomicLong ids = new AtomicLong();


    public CraftScheduler() {
        this(Thread.currentThread());
    }

    public CraftScheduler(Thread owner) {
        this.delayedQueue = new DelayedWorkQueue();
    }

    @Override
    public void run() {
        tick++;
        for (CraftTask<?> task; (task = workQueue.poll()) != null;) {
            if (task.isPeriodic()) {
                delayedQueue.push(task);
            } else {
                task.run();
            }
        }
        delayedQueue.releaseAll();
    }


    @Override
    public void close() throws Exception {

    }


    public interface WorkQueue {

        boolean tryPush(CraftTask<?> task);

        default void push(CraftTask<?> task) {
            while (!tryPush(task));
        }

        CraftTask<?> poll();

        default void releaseAll() {
            for (CraftTask<?> task; (task = poll()) != null;) {
                task.run();
            }
        }
    }

    private static class LinkedQueue implements WorkQueue {
        private volatile QNode head, tail;

        public LinkedQueue(CraftTask<?> first) {
            head = tail = new QNode(first);
        }
        @Override
        public void push(CraftTask<?> task) {
            QNode node = new QNode(task);
            QNode t = (QNode) TAIL.getAndSet(this, node);
            t.next = node;
        }
        @Override
        public boolean tryPush(CraftTask<?> task) {
            final QNode node = new QNode(task), t;
            if (TAIL.weakCompareAndSet(this, t = tail, node)) {
                t.next = node;
                return true;
            }
            return false;
        }

        @Override
        public CraftTask<?> poll() {
            final QNode e = head;
            if (e != null) {
                head = e.next;
                e.next = null; // help gc
                return e.task;
            }
            return null;
        }

        @Override
        public void releaseAll() {
            QNode last = null;
            for (QNode e = head; e != null; e = e.next) {
                e.task.run();
                last = e;
            }
            head = last;
        }
        private static final VarHandle TAIL;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                TAIL = l.findVarHandle(LinkedQueue.class,
                        "tail", QNode.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        private static class QNode {
            final CraftTask<?> task;

            volatile QNode next;

            QNode(CraftTask<?> x) { task = x; }
        }
    }
    private static class StripedQueue implements WorkQueue {
        private static final int NCPU = Runtime.getRuntime().availableProcessors();

        private volatile WorkQueue[] queues;
        private volatile boolean busy;

        @Override
        public void releaseAll() {
            final WorkQueue[] a = queues;
            if (a == null)
                return;
            for (WorkQueue x : a) {
                if (x == null)
                    continue;
                x.releaseAll();
            }
        }

        @Override
        public boolean tryPush(CraftTask<?> task) {
            int id = (int) Thread.currentThread().threadId();
            for (boolean grow = false;;) {
                WorkQueue[] m = queues;
                if (m == null) {
                    WorkQueue[] rs = new WorkQueue[2];
                    rs[id & 1] = new LinkedQueue(task);
                    WorkQueue[] w = (WorkQueue[])
                            QS.compareAndExchange(this, null, rs);
                    if (w == null)
                        break;
                    else
                        m = w;
                }
                int n = m.length, h = (id & (n - 1));
                WorkQueue q = m[h];
                if (q == null) {
                    WorkQueue rs = new LinkedQueue(task);
                    WorkQueue w = (WorkQueue)
                            AA.compareAndExchange(m, h, null, rs);
                    if (w == null)
                        break;
                    else
                        q = w;
                }
                if (grow || n >= NCPU) {
                    q.push(task);
                    break;
                } else if (q.tryPush(task)) {
                    break;
                } else if (!busy && BUSY.compareAndSet(
                        this, false, true)) {
                    try {
                        queues = Arrays.copyOf(m, n << 1);
                        grow = true;
                    } finally {
                        busy = false;
                    }
                }
                Thread.onSpinWait();
            }
            return true;
        }
        @Override
        public CraftTask<?> poll() {
            final WorkQueue[] a = queues;
            if (a == null)
                return null;
            for (WorkQueue x : a) {
                if (x == null)
                    continue;
                CraftTask<?> t = x.poll();
                if (t != null)
                    return t;
            }
            return null;
        }

        private static final VarHandle AA
                = MethodHandles.arrayElementVarHandle(WorkQueue[].class);
        private static final VarHandle BUSY, QS;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                BUSY = l.findVarHandle(StripedQueue.class,
                        "busy", boolean.class);
                QS = l.findVarHandle(StripedQueue.class,
                        "queues", WorkQueue[].class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static final class DelayedWorkQueue implements WorkQueue {
        private final Queue<CraftTask<?>> internal = new PriorityQueue<>();

        @Override
        public boolean tryPush(CraftTask<?> task) {
            return internal.add(task);
        }

        @Override
        public CraftTask<?> poll() {
            CraftTask<?> first = internal.peek();
            return (first == null || first.getDelay(NANOSECONDS) > 0)
                    ? null
                    : internal.poll();
        }
    }
    public static int currentTick() {
        return tick;
    }

    public <V> RunnableScheduledFuture<V> schedule(Runnable runnable, long delay, long period) {
        final long id = ids.getAndIncrement();
        Runnable command = new Runnable() {
            @Override
            public void run() {
                runnable.run();
                submit(this, id, period);
            }
        };
        CraftTask<V> root = submit(command, id, delay);
        return new ForwardTask<>(id, root);
    }
    public <V> CraftTask<V> submit(Runnable action, long id, long delay) {
        final CraftTask<V> task = new CraftTask<>(action,
                null,
                tick + delay,
                id
        );
        tasks.put(id, task);
        workQueue.push(task);
        return task;
    }
    private class ForwardTask<V> implements RunnableScheduledFuture<V> {

        private final long id;
        private volatile CraftTask<V> last;

        private ForwardTask(long id, CraftTask<V> root) {
            this.id = id;
            this.last = root;
        }

        private CraftTask<V> task() {
            CraftTask<?> t = tasks.get(id);
            return t == null ? last : (last = (CraftTask<V>) t);
        }

        @Override
        public boolean isPeriodic() {
            return task().isPeriodic();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return task().getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return task().compareTo(o);
        }

        @Override
        public void run() {
            task().run();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return task().isPeriodic();
        }

        @Override
        public boolean isCancelled() {
            return task().isCancelled();
        }

        @Override
        public boolean isDone() {
            return task().isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return task().get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return task().get(timeout, unit);
        }
    }
}
