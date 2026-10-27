package learn.gjx.tuku1.manager.space;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

class UserSpaceCreationLockTest {
    @Test
    void sameUserNeverExecutesConcurrentlyAndAllEntriesAreReleased() throws Exception {
        UserSpaceCreationLock lock = new UserSpaceCreationLock();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 200; i++) {
                tasks.add(pool.submit(() -> lock.execute(1L, () -> {
                    assertEquals(1, active.incrementAndGet());
                    Thread.yield();
                    completed.incrementAndGet();
                    assertEquals(0, active.decrementAndGet());
                    return null;
                })));
            }
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            assertEquals(200, completed.get());
            assertEquals(0, lock.retainedUserCount());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void differentUsersCanEnterAtTheSameTime() throws Exception {
        UserSpaceCreationLock lock = new UserSpaceCreationLock();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> first = pool.submit(() -> lock.execute(1L, () -> {
                entered.countDown();
                await(entered);
                return null;
            }));
            Future<?> second = pool.submit(() -> lock.execute(2L, () -> {
                entered.countDown();
                await(entered);
                return null;
            }));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(0, lock.retainedUserCount());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void exceptionAndReentrantExecutionDoNotLeakOrPrematurelyRemoveLock() {
        UserSpaceCreationLock lock = new UserSpaceCreationLock();
        lock.execute(1L, () -> {
            lock.execute(1L, () -> null);
            assertEquals(1, lock.retainedUserCount());
            return null;
        });
        assertEquals(0, lock.retainedUserCount());
        assertThrows(IllegalStateException.class, () -> lock.execute(1L, () -> {
            throw new IllegalStateException("failure");
        }));
        assertEquals(0, lock.retainedUserCount());
        assertEquals("retry", lock.execute(1L, () -> "retry"));
    }

    @Test
    void queuedWaiterKeepsSameLockWhenFirstCallerLeavesAndThirdArrives() throws Exception {
        UserSpaceCreationLock lock = new UserSpaceCreationLock();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        try {
            Future<?> first = pool.submit(() -> lock.execute(1L, () -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }));
            await(firstEntered);
            Future<?> second = pool.submit(() -> {
                secondThread.set(Thread.currentThread());
                return lock.execute(1L, () -> {
                    secondEntered.countDown();
                    await(releaseSecond);
                    return null;
                });
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((secondThread.get() == null || secondThread.get().getState() != Thread.State.BLOCKED)
                    && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertNotNull(secondThread.get());
            assertEquals(Thread.State.BLOCKED, secondThread.get().getState());
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            await(secondEntered);
            assertEquals(1, lock.retainedUserCount(), "等待者仍在使用，不可提前移除锁");
            CountDownLatch thirdStarted = new CountDownLatch(1);
            Future<?> third = pool.submit(() -> {
                thirdStarted.countDown();
                return lock.execute(1L, () -> null);
            });
            await(thirdStarted);
            assertThrows(TimeoutException.class, () -> third.get(200, TimeUnit.MILLISECONDS));
            releaseSecond.countDown();
            second.get(10, TimeUnit.SECONDS);
            third.get(10, TimeUnit.SECONDS);
            assertEquals(0, lock.retainedUserCount());
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
