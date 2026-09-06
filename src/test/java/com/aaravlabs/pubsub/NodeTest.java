package com.aaravlabs.pubsub;

import com.aaravlabs.pubsub.annotation.RunPeriodically;
import com.aaravlabs.pubsub.annotation.SubscribedTo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {

    private Orchestrator orch;

    @BeforeEach void setUp() { orch = Orchestrator.create("test"); }
    @AfterEach  void tearDown() { orch.close(); }

    static class SubscriberNode extends Node {
        final List<String> events = new ArrayList<>();
        SubscriberNode(Orchestrator o) { super(o); }
        @SubscribedTo(topic = "ping")
        public void onPing(String msg) { events.add(msg); }
    }

    static class PeriodicNode extends Node {
        final AtomicInteger ticks = new AtomicInteger();
        PeriodicNode(Orchestrator o) { super(o); }
        @RunPeriodically(hz = 100)
        public void tick() { ticks.incrementAndGet(); }
    }

    @Test
    void subscribedTo_isInvokedOnPublish() throws Exception {
        SubscriberNode n = new SubscriberNode(orch);
        orch.getOrCreateTopic("ping", String.class);
        orch.registerNode("sub", n);

        orch.publish("ping", "hello");
        for (int i = 0; i < 50 && n.events.isEmpty(); i++) Thread.sleep(10);
        assertEquals(List.of("hello"), n.events);
    }

    @Test
    void runPeriodically_firesAtApproximateRate() throws Exception {
        PeriodicNode n = new PeriodicNode(orch);
        orch.registerNode("periodic", n);

        Thread.sleep(120); // ~12 ticks at 100 Hz
        int after = n.ticks.get();
        assertTrue(after >= 8 && after <= 20,
                "expected ~12 ticks in 120ms at 100Hz, got " + after);
    }

    @Test
    void unregisterNode_stopsSubscriptionsAndPeriodic() throws Exception {
        SubscriberNode n = new SubscriberNode(orch);
        PeriodicNode p = new PeriodicNode(orch);
        orch.getOrCreateTopic("ping", String.class);
        orch.registerNode("sub", n);
        orch.registerNode("periodic", p);

        orch.unregisterNode("sub");
        orch.publish("ping", "after-unregister");
        Thread.sleep(50);
        assertTrue(n.events.isEmpty(), "should not receive after unregister");

        int before = p.ticks.get();
        orch.unregisterNode("periodic");
        Thread.sleep(80);
        int after = p.ticks.get();
        assertEquals(before, after, "periodic should stop after unregister");
    }

    @Test
    void multipleSubscribedToOnSameMethod() throws Exception {
        orch.getOrCreateTopic("a", String.class);
        orch.getOrCreateTopic("b", String.class);

        class N extends Node {
            final List<String> seen = new ArrayList<>();
            N(Orchestrator o) { super(o); }
            @SubscribedTo(topic = "a")
            @SubscribedTo(topic = "b")
            public void onAny(String msg) { seen.add(msg); }
        }

        N n = new N(orch);
        orch.registerNode("n", n);

        orch.publish("a", "1");
        orch.publish("b", "2");
        for (int i = 0; i < 50 && n.seen.size() < 2; i++) Thread.sleep(10);
        assertTrue(n.seen.contains("1"));
        assertTrue(n.seen.contains("2"));
    }
}
