package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Orchestrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HardwareActionsTest {

    private Orchestrator orch;
    private HardwareActions hw;

    @BeforeEach void setUp() { orch = Orchestrator.create("test"); hw = orch.hardware(); }
    @AfterEach  void tearDown() { orch.close(); }

    @Test
    void run_runsOnHardwareThread() throws Exception {
        AtomicReference<String> tname = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        hw.run(() -> {
            tname.set(Thread.currentThread().getName());
            done.countDown();
        });
        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(tname.get().contains("-hw-"),
                "hw.run should execute on hardware thread, got: " + tname.get());
    }

    @Test
    void call_returnsValueAndRunsOnHardwareThread() throws Exception {
        String result = hw.call(() -> {
            assertTrue(Thread.currentThread().getName().contains("-hw-"));
            return "hello-from-hw";
        });
        assertEquals("hello-from-hw", result);
    }

    @Test
    void call_blocksUntilHardwareThreadCompletes() throws Exception {
        // Verify call() blocks: schedule a slow task and time it.
        long t0 = System.nanoTime();
        String s = hw.call(() -> {
            try { Thread.sleep(80); } catch (InterruptedException ignored) {}
            return "slow";
        });
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals("slow", s);
        assertTrue(elapsedMs >= 60, "call() should block at least 60ms for the slow task; took " + elapsedMs + "ms");
    }

    @Test
    void callAsync_returnsFuture() throws Exception {
        CompletableFuture<Integer> f = hw.callAsync(() -> 42);
        // Don't immediately expect completion; let the hardware thread pick it up.
        Thread.sleep(50);
        assertEquals(Integer.valueOf(42), f.get(1, TimeUnit.SECONDS));
    }

    @Test
    void callAsync_completesExceptionallyOnError() throws Exception {
        CompletableFuture<Void> f = hw.callAsync(() -> {
            throw new IllegalArgumentException("boom");
        });
        Thread.sleep(50);
        ExecutionExceptionAsserts.assertExecutionException(f, IllegalArgumentException.class, "boom");
    }

    @Test
    void isHardwareThread_isFalseOnOtherThread() {
        assertFalse(hw.isHardwareThread(), "main test thread should not be the hardware thread");
    }

    @Test
    void isHardwareThread_isTrueOnHardwareThread() throws Exception {
        hw.run(() -> assertTrue(hw.isHardwareThread()));
        Thread.sleep(50); // give the task time to run
    }

    @Test
    void assertNotHardwareThread_throwsOnHardwareThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        hw.run(() -> {
            try {
                hw.assertNotHardwareThread();
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertNotNull(caught.get(), "assertNotHardwareThread should throw when invoked from hardware thread");
        assertTrue(caught.get() instanceof IllegalStateException,
                "expected IllegalStateException, got " + caught.get().getClass());
    }

    @Test
    void bulkRead_publishesValuesAtFixedRate() throws Exception {
        AtomicInteger tickCount = new AtomicInteger();
        List<Integer> publishedValues = new CopyOnWriteArrayList<>();

        HardwareActions.BulkReadHandle handle = hw.bulkRead(50, view -> {
            int n = tickCount.incrementAndGet();
            view.publish("bulk/tick", n);
        });
        try {
            // Subscribe and wait for at least 3 publishes (~60ms).
            orch.subscribe("bulk/tick", Integer.class, publishedValues::add);
            Thread.sleep(300);
            assertTrue(publishedValues.size() >= 3,
                    "bulk-read should produce at least 3 ticks; got " + publishedValues.size());
            // Values should be monotonically increasing (1, 2, 3, ...).
            for (int i = 0; i < publishedValues.size() - 1; i++) {
                assertTrue(publishedValues.get(i) < publishedValues.get(i + 1),
                        "bulk-read ticks should be monotonic; got " + publishedValues);
            }
        } finally {
            hw.stopBulkRead(handle);
        }
    }

    @Test
    void bulkRead_callbackRunsOnHardwareThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> tname = new AtomicReference<>();
        HardwareActions.BulkReadHandle handle = hw.bulkRead(50, view -> {
            if (tname.compareAndSet(null, Thread.currentThread().getName())) {
                done.countDown();
            }
        });
        try {
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertTrue(tname.get().contains("-hw-"),
                    "bulk-read callback should run on hardware thread; got: " + tname.get());
        } finally {
            hw.stopBulkRead(handle);
        }
    }

    @Test
    void stopBulkRead_stopsPeriodic() throws Exception {
        AtomicInteger ticks = new AtomicInteger();
        HardwareActions.BulkReadHandle handle = hw.bulkRead(100, view -> ticks.incrementAndGet());
        Thread.sleep(120);
        int before = ticks.get();
        assertTrue(before >= 5, "should have ticked several times before cancel; got " + before);

        hw.stopBulkRead(handle);
        Thread.sleep(150);
        int after = ticks.get();
        Thread.sleep(150);
        int evenLater = ticks.get();
        assertEquals(after, evenLater, "ticks should not advance after stopBulkRead");
    }
}

class ExecutionExceptionAsserts {
    static void assertExecutionException(CompletableFuture<?> f, Class<? extends Throwable> cause, String messageFragment) {
        try {
            f.get(1, TimeUnit.SECONDS);
            fail("expected ExecutionException");
        } catch (Exception e) {
            assertTrue(e.getClass().getSimpleName().equals("ExecutionException"),
                    "expected ExecutionException, got " + e.getClass());
            assertNotNull(e.getCause(), "ExecutionException should have a cause");
            assertTrue(cause.isInstance(e.getCause()),
                    "cause should be " + cause.getName() + ", got " + e.getCause().getClass());
            assertTrue(e.getCause().getMessage() != null && e.getCause().getMessage().contains(messageFragment),
                    "cause message should contain '" + messageFragment + "', got: " + e.getCause().getMessage());
        } catch (Throwable t) {
            fail("unexpected: " + t);
        }
    }
}
