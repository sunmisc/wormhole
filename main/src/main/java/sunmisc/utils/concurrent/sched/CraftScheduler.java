package sunmisc.utils.concurrent.sched;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class CraftScheduler implements Runnable {

    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    private static volatile int tick;

    private final StripedQueue disposable = new StripedQueue();
    private final PriorityQueue<CraftTask<?>> internal = new PriorityQueue<>();
    private final DelayQueue<CraftTask<?>> external = new DelayQueue<>();
    private final ConcurrentMap<Long, CraftTask<?>> tasks =
            new ConcurrentHashMap<>();
    private final Thread owner;

    private volatile long ids;

    public CraftScheduler() {
        this(Thread.currentThread());
    }

    public CraftScheduler(Thread owner) {
        this.owner = owner;
    }

    public static void main(String[] args) throws InterruptedException {
        CraftScheduler scheduler = new CraftScheduler();

        new Thread(() -> {
           scheduler.schedule(() -> {
               System.out.println("kek");
           }, 20);
        }).start();
        while (true) {
            scheduler.run();
            Thread.sleep(50);
        }
    }
    @Override
    public void run() {
        tick++;
        disposable.executeAll();
        for (CraftTask<?> x;
             (x = internal.peek()) != null &&
                x.getDelay(NANOSECONDS) <= 0; ) {
            x.run();
            internal.remove();
        }
        for (CraftTask<?> x; (x = external.poll()) != null; ) {
            x.run();
        }
    }
    public static int currentTick() {
        return tick;
    }

    public <V> CraftTask<V> schedule(Runnable runnable, long period) {
        long id = (long) IDS.getAndAdd(this, 1);
        return submit(new Runnable() {
            @Override
            public void run() {
                runnable.run();
                submit(this, id, period);
            }
        }, id, 0);
    }
    private <V> CraftTask<V> submit(Runnable action, long id, long delay) {
        final CraftTask<V> task = new CraftTask<>(action,
                null,
                tick + delay,
                id
        );
        tasks.put(id, task);
        if (delay > 0) {
            Thread wt = Thread.currentThread();
            (wt == owner ? internal : external).add(task);
        } else {
            disposable.push(task);
        }
        return task;
    }
    private static class WorkQueue {
        private volatile QNode head, tail;

        public WorkQueue(CraftTask<?> first) {
            head = tail = new QNode(first);
        }
        public void push(CraftTask<?> task) {
            QNode node = new QNode(task);
            QNode t = (QNode) TAIL.getAndSet(this, node);
            t.next = node;
        }
        public boolean tryPush(CraftTask<?> task) {
            QNode node = new QNode(task);
            final QNode t = tail;
            if (TAIL.compareAndSet(this, t, node)) {
                t.next = node;
                return true;
            }
            return false;
        }
        public void execute() {
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
                TAIL = l.findVarHandle(WorkQueue.class,
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
    private static class StripedQueue {
        private volatile WorkQueue[] queues;
        private volatile boolean busy;

        public void executeAll() {
            WorkQueue[] a = queues;
            if (a == null) return;
            for (WorkQueue x : a) {
                if (x == null) continue;
                x.execute();
            }
        }

        public void push(CraftTask<?> task) {
            int id = (int) Thread.currentThread().threadId();
            for (boolean grow = false;;) {
                WorkQueue[] m = queues;
                if (m == null) {
                    WorkQueue[] rs = new WorkQueue[2];
                    rs[id & 1] = new WorkQueue(task);
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
                    WorkQueue rs = new WorkQueue(task);
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
    private static final VarHandle IDS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            IDS = l.findVarHandle(CraftScheduler.class,
                    "ids", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
