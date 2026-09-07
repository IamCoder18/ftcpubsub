package com.aaravlabs.synapse;

import com.aaravlabs.synapse.annotation.OnHardwareThread;
import com.aaravlabs.synapse.annotation.RunPeriodically;
import com.aaravlabs.synapse.annotation.SubscribedTo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link com.aaravlabs.synapse.annotation.OnHardwareThread @OnHardwareThread}
 * mechanism. All hardware-thread work in the orchestrator must execute on a single
 * dedicated thread, strictly serially, so that hardware-touching code never runs
 * concurrently.
 */
class HardwareThreadTest {

    private Orchestrator orch;

    @BeforeEach void setUp() {
        // The orchestrator name is intentionally NOT containing "hw" so the
        // hardware-thread prefix "-hw-" is an unambiguous marker.
        orch = Orchestrator.create("robot");
    }
    @AfterEach  void tearDown() { orch.close(); }

    /**
     * A fake "hardware device" with a non-thread-safe counter. The counter only
     * increments correctly if all writes happen on a single thread.
     */
    static class FakeHardware {
        int value = 0;
        // Each transaction increments, sleeps, then decrements. If two threads
        // interleave, the value can be observed mid-transaction as nonzero AND
        // we'd see two "+" events back-to-back with no "-" between.
        synchronized void transaction(String threadName, List<String> events) {
            int seen = value;
            value = seen + 1;
            events.add("write+" + threadName);
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
            value = value - 1;
            events.add("write-" + threadName);
        }
    }

    static class HardwareSubscriberNode extends Node {
        final FakeHardware hw = new FakeHardware();
        final List<String> events = new CopyOnWriteArrayList<>();
        HardwareSubscriberNode(Orchestrator o) { super(o); }

        @SubscribedTo(topic = "hw/write")
        @OnHardwareThread
        public void onWrite(String who) {
            hw.transaction(who, events);
        }
    }

    @Test
    void allHardwareWorkRunsOnASingleThread() throws Exception {
        HardwareSubscriberNode node = new HardwareSubscriberNode(orch);
        orch.getOrCreateTopic("hw/write", String.class);
        orch.registerNode("hw", node);

        // Hammer the topic from many threads.
        Thread[] publishers = new Thread[8];
        for (int i = 0; i < publishers.length; i++) {
            publishers[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) orch.publish("hw/write", "pub-" + Thread.currentThread().getId());
            });
            publishers[i].start();
        }
        for (Thread t : publishers) t.join();

        // The publish() path goes publisher-thread → callback-pool → hardware-thread.
        // We need to drain BOTH the callback pool AND the hardware thread before
        // taking the snapshot. The fence below only guarantees the hardware
        // thread is past all enqueued-at-that-moment work; callback-pool tasks
        // still in flight can enqueue MORE hardware work after the fence. So
        // first drain the callback pool by waiting until the published-message
        // count stabilises, then run the hardware-thread fence.
        for (int spin = 0; spin < 50; spin++) {
            Thread.sleep(50);
            int currentCount = node.events.size();
            Thread.sleep(50);
            if (node.events.size() == currentCount) break;
        }

        // Now the callback pool is drained. Fence the hardware thread to ensure
        // all in-flight hardware tasks have completed.
        CountDownLatch drained = new CountDownLatch(1);
        orch.runOnHardwareThread(drained::countDown);
        assertTrue(drained.await(5, TimeUnit.SECONDS),
                "hardware thread should drain in <5s after publishers join");

        // Unregister so @AfterEach close doesn't interrupt an in-flight task.
        orch.unregisterNode("hw");

        // Verify the +/- pairing invariant. If hardware-thread serial execution
        // were broken, we'd see "+pub-X" followed by "+pub-Y" before any "-".
        assertEquals(0, node.events.size() % 2,
                "events must come in +/- pairs; got " + node.events);
        for (int i = 0; i < node.events.size() - 1; i += 2) {
            String a = node.events.get(i);
            String b = node.events.get(i + 1);
            assertTrue(a.startsWith("write+"),
                    "expected write+ at index " + i + ", got " + a + " (events=" + node.events + ")");
            assertTrue(b.startsWith("write-"),
                    "expected write- at index " + (i + 1) + ", got " + b + " (events=" + node.events + ")");
            assertEquals(a.substring("write+".length()), b.substring("write-".length()),
                    "+ and - must be from the same thread");
        }
    }

    @Test
    void nonHardwareCallbacksStillRunOnCallbackPool() throws Exception {
        AtomicReference<String> callbackThreadName = new AtomicReference<>();
        orch.subscribe("regular", String.class, msg -> callbackThreadName.set(Thread.currentThread().getName()));

        orch.publish("regular", "x");
        for (int i = 0; i < 100 && callbackThreadName.get() == null; i++) Thread.sleep(10);

        assertNotNull(callbackThreadName.get());
        // Callback-pool threads are named with the "synapse-hw-test-" prefix via
        // our ThreadFactory. Hardware thread uses "synapse-hw-test-hw-" prefix.
        // A callback-pool thread does NOT have the "-hw-" suffix.
        assertFalse(callbackThreadName.get().contains("-hw-"),
                "regular callback should run on callback pool, not hardware thread; got: "
                        + callbackThreadName.get());
    }

    @Test
    void hardwareCallbackRunsOnHardwareThread() throws Exception {
        AtomicReference<String> hwThreadName = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        class N extends Node {
            N(Orchestrator o) { super(o); }
            @SubscribedTo(topic = "x")
            @OnHardwareThread
            public void onX(String s) {
                hwThreadName.set(Thread.currentThread().getName());
                done.countDown();
            }
        }
        orch.getOrCreateTopic("x", String.class);
        orch.registerNode("n", new N(orch));
        orch.publish("x", "hello");

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertNotNull(hwThreadName.get());
        assertTrue(hwThreadName.get().contains("-hw-"),
                "hardware callback should run on hardware thread; got: " + hwThreadName.get());
    }

    @Test
    void hardwarePeriodicRunsOnHardwareThread() throws Exception {
        AtomicReference<String> hwThreadName = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        class N extends Node {
            N(Orchestrator o) { super(o); }
            @RunPeriodically(hz = 50, hardware = true)
            public void tick() {
                if (hwThreadName.compareAndSet(null, Thread.currentThread().getName())) {
                    done.countDown();
                }
            }
        }
        orch.registerNode("n", new N(orch));
        assertTrue(done.await(2, TimeUnit.SECONDS), "hardware periodic should tick at least once");
        assertNotNull(hwThreadName.get());
        assertTrue(hwThreadName.get().contains("-hw-"),
                "hardware periodic should run on hardware thread; got: " + hwThreadName.get());
    }

    @Test
    void runOnHardwareThreadRunsOnHardwareThread() throws Exception {
        orch.runOnHardwareThread(() -> {
            assertTrue(Thread.currentThread().getName().contains("-hw-"));
        });
        // Give the executor time.
        Thread.sleep(100);
    }

    @Test
    void mixedRegularAndHardwareCallbacksDispatchToTheirRespectiveThreads() throws Exception {
        // Publishes to a topic that has BOTH a regular subscriber and a hardware
        // subscriber. Both should fire, each on its own thread.
        AtomicReference<String> regularThread = new AtomicReference<>();
        AtomicReference<String> hwThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        class N extends Node {
            N(Orchestrator o) { super(o); }
            @SubscribedTo(topic = "both")
            @OnHardwareThread
            public void onHw(String s) {
                hwThread.set(Thread.currentThread().getName());
                done.countDown();
            }
        }
        orch.getOrCreateTopic("both", String.class);
        orch.registerNode("n", new N(orch));
        orch.subscribe("both", String.class, s -> regularThread.set(Thread.currentThread().getName()));

        orch.publish("both", "x");

        assertTrue(done.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < 100 && regularThread.get() == null; i++) Thread.sleep(10);

        assertNotNull(regularThread.get());
        assertNotNull(hwThread.get());
        assertNotEquals(regularThread.get(), hwThread.get(),
                "regular and hardware callbacks should run on different threads; got the same: "
                        + regularThread.get());
    }
}
