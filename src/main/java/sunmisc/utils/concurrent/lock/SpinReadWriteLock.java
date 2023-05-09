package sunmisc.utils.concurrent.lock;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class SpinReadWriteLock implements ReadWriteLock {

    private final LockAdder lockAdder = new LockAdder();

    private final ReentrantLock global = new ReentrantLock();

    private final ReadLock readLock = new ReadLock(this);

    private final WriteLock writeLock = new WriteLock(this);
    @NotNull
    @Override
    public Lock readLock() {
        return readLock;
    }

    @NotNull
    @Override
    public Lock writeLock() {
        return writeLock;
    }

    private record WriteLock(SpinReadWriteLock src) implements Lock {
        @Override
        public void lock() {
            src.global.lock();
            postLock();
        }

        private void postLock() {

            LockAdder a = src.lockAdder;

            a.forceLock = true;

            while (a.waiting() != 0)
                Thread.onSpinWait();
        }

        @Override
        public void unlock() {

            src.lockAdder.forceLock = false;

            src.global.unlock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            src.global.lockInterruptibly();
            postLock();
        }

        @Override
        public boolean tryLock() {
            boolean locked = src.global.tryLock();
            if (locked)
                postLock();
            return locked;
        }

        @Override
        public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
            boolean locked = src.global.tryLock(time, unit);
            if (locked)
                postLock();
            return locked;
        }


        @NotNull
        @Override
        public Condition newCondition() {
            return src.global.newCondition();
        }
    }

    private record ReadLock(SpinReadWriteLock src) implements Lock {

        @Override
        public void lock() {
            LockAdder a = src.lockAdder;
            if (a.forceLock) // isLocked
                src.global.lock();
            else
                a.inc();
        }

        @Override
        public void unlock() {
            if (src.global.isHeldByCurrentThread())
                src.global.unlock();
            else
                src.lockAdder.dec();
        }


        @Override
        public void lockInterruptibly() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            lock();
        }

        @Override
        public boolean tryLock() {
            LockAdder a = src.lockAdder;
            if (a.forceLock)
                return src.global.tryLock();
            else
                a.inc();
            return true;
        }

        @Override
        public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
            LockAdder a = src.lockAdder;
            if (a.forceLock)
                return src.global.tryLock(time, unit);
            else
                a.inc();
            return true;
        }

        @NotNull
        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    private static class LockAdder {
        final LongAdder adder = new LongAdder();

        volatile boolean forceLock;

        public void inc() {
            adder.increment();
        }

        public void dec() {
            adder.decrement();
        }

        public long waiting() {
            return adder.sum();
        }
    }
}