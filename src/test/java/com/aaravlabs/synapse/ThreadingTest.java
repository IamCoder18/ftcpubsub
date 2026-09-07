package com.aaravlabs.synapse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the two-pool design: a slow callback should not be able to starve a
 * periodic loop running on the scheduler pool.
 */
class ThreadingTest {

    @Test
    void slowCallback_doesNotBlockPeriodicLoop() throws Exception {
        Orchestrator orch = Orchestrator.create("test");
        try {
            orch.getOrCreateTopic("slow", String.class);

            List<String> received = new CopyOnWriteArrayList<>();
            orch.subscribe("slow", String.class, msg -> {
                // Sleep inside the callback to flood the callback pool.
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                received.add(msg);
            });

            // Periodic loop running on the SCHEDULER pool.
            java.util.concurrent.atomic.AtomicInteger ticks = new java.util.concurrent.atomic.AtomicInteger();
            orch.runPeriodically(ticks::incrementAndGet, 100);

            // Publish a flurry of slow messages.
            for (int i = 0; i < 20; i++) orch.publish("slow", "msg-" + i);

            // Sleep long enough for the scheduler pool to have ticked many times.
            Thread.sleep(300);

            int afterTicks = ticks.get();
            assertTrue(afterTicks >= 20,
                    "scheduler pool should be unaffected by slow callbacks; ticks=" + afterTicks);

            // Callbacks eventually drained (backpressure, not drop).
            // We don't assert exact count because of CallerRunsPolicy timing.
        } finally {
            orch.close();
        }
    }

    @Test
    void close_shutsDownAllPools() throws Exception {
        Orchestrator orch = Orchestrator.create("test");
        orch.runPeriodically(() -> {}, 50);
        orch.getOrCreateTopic("t", String.class);
        orch.subscribe("t", String.class, s -> {});
        orch.publish("t", "x");
        Thread.sleep(20);

        orch.close();
        assertTrue(orch.isClosed());

        // Second close should be a no-op.
        orch.close();
        assertTrue(orch.isClosed());
    }
}
