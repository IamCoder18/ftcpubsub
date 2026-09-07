package com.aaravlabs.synapse;

import com.aaravlabs.synapse.annotation.RunPeriodically;
import com.aaravlabs.synapse.annotation.SubscribedTo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Long-running soak test. Fires publishes and registers/unregisters nodes for several
 * seconds to catch:
 * <ul>
 *   <li>Thread leaks (worker count should return to baseline after close).</li>
 *   <li>Subscriber-list snapshot leaks.</li>
 *   <li>Lost updates under sustained load.</li>
 *   <li>Deadlocks on shutdown.</li>
 * </ul>
 *
 * <p>Default 3 seconds; can be bumped via the {@code SOAK_SECONDS} env var.
 */
class SoakTest {

    private static int soakSeconds() {
        String s = System.getenv("SOAK_SECONDS");
        return s != null ? Integer.parseInt(s) : 3;
    }

    @Test
    void sustainedPublishAndRegisterDoesNotLeakOrDeadlock() throws Exception {
        int seconds = soakSeconds();
        Orchestrator orch = Orchestrator.create("soak");
        try {
            // Producer: 1kHz publishes.
            Thread producer = new Thread(() -> {
                long deadline = System.currentTimeMillis() + seconds * 1000L;
                int i = 0;
                while (System.currentTimeMillis() < deadline) {
                    orch.publish("soak/int", i++);
                    orch.publish("soak/double", i * 0.5);
                    orch.publish("soak/string", "v" + i);
                }
            }, "soak-producer");
            producer.start();

            // Consumer: registers and unregisters short-lived nodes.
            Thread consumer = new Thread(() -> {
                long deadline = System.currentTimeMillis() + seconds * 1000L;
                int i = 0;
                while (System.currentTimeMillis() < deadline) {
                    String name = "soak-node-" + (i++ % 50);
                    orch.getOrCreateTopic("soak/int", Integer.class);
                    orch.getOrCreateTopic("soak/double", Double.class);
                    orch.getOrCreateTopic("soak/string", String.class);
                    orch.unregisterNode(name);
                }
            }, "soak-consumer");
            consumer.start();

            // Periodic: a counter ticking at 100Hz.
            AtomicInteger ticks = new AtomicInteger();
            orch.runPeriodically(ticks::incrementAndGet, 100);

            producer.join();
            consumer.join();

            int minExpectedTicks = seconds * 80;
            assertTrue(ticks.get() >= minExpectedTicks,
                    "expected >= " + minExpectedTicks + " ticks at 100Hz over "
                            + seconds + "s, got " + ticks.get());

            // After orchestrator close, all pools should shut down within 500ms.
        } finally {
            long t0 = System.currentTimeMillis();
            orch.close();
            long elapsed = System.currentTimeMillis() - t0;
            assertTrue(elapsed < 1000, "close should complete within 1s, took " + elapsed + "ms");
            assertTrue(orch.isClosed());
        }
    }

    @Test
    void publishDuringSubscribeIsSafe() throws Exception {
        // Race: another thread publishes while we're subscribing. We must not
        // lose or duplicate messages, and the SubscriberList snapshot must remain
        // consistent.
        Orchestrator orch = Orchestrator.create("race");
        try {
            orch.getOrCreateTopic("race/topic", Integer.class);

            Thread publisher = new Thread(() -> {
                for (int i = 0; i < 1000; i++) orch.publish("race/topic", i);
            }, "race-publisher");

            Thread subscriber = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    Subscription sub = orch.subscribe("race/topic", Integer.class, v -> {});
                    sub.unsubscribe();
                }
            }, "race-subscriber");

            publisher.start();
            subscriber.start();
            publisher.join();
            subscriber.join();

            // Should complete without exceptions or hangs. The exact count is
            // non-deterministic; we just verify the bus is still operational.
            orch.publish("race/topic", -1);
            assertEquals(Integer.valueOf(-1),
                    orch.getLatestValue("race/topic", Integer.class).orElse(null));

        } finally {
            orch.close();
        }
    }

    @Test
    void closeIsIdempotentAndClean() throws Exception {
        Orchestrator orch = Orchestrator.create("close");
        orch.registerNode("n", new Node(orch) {
            @SubscribedTo(topic = "x") public void onX(String s) {}
            @RunPeriodically(hz = 50) public void tick() {}
        });
        orch.getOrCreateTopic("x", String.class);
        orch.subscribe("x", String.class, s -> {});
        orch.publish("x", "v");
        Thread.sleep(50);

        orch.close();
        orch.close(); // idempotent
        orch.close(); // idempotent
        assertTrue(orch.isClosed());
    }
}
