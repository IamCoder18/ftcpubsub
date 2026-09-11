package com.aaravlabs.synapse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class TopicTest {

    /** Async callback tests poll every 10 ms for up to 5 s; CI runners can be slow to schedule callback threads. */
    private static final int POLL_INTERVAL_MS = 10;
    private static final int AWAIT_BUDGET_MS = 5000;

    /** Waits until {@code cond} is true or the budget elapses. */
    private static void await(BooleanSupplier cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_BUDGET_MS;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_INTERVAL_MS);
    }

    private Orchestrator orch;

    @BeforeEach void setUp() { orch = Orchestrator.create("test"); }
    @AfterEach  void tearDown() { orch.close(); }

    @Test
    void getOrCreate_returnsSameTopic() {
        Topic<String> a = orch.getOrCreateTopic("t", String.class);
        Topic<String> b = orch.getOrCreateTopic("t", String.class);
        assertSame(a, b);
    }

    @Test
    void getOrCreate_throwsOnTypeMismatch() {
        orch.getOrCreateTopic("t", String.class);
        assertThrows(IllegalArgumentException.class,
                () -> orch.getOrCreateTopic("t", Integer.class));
    }

    @Test
    void latestValue_isEmptyBeforeAnyPublish() {
        Topic<String> t = orch.getOrCreateTopic("t", String.class);
        assertTrue(t.latestValue().isEmpty());
        assertNull(t.latestValueOr(null));
    }

    @Test
    void latestValue_updatesAfterPublish() {
        orch.getOrCreateTopic("t", String.class);
        orch.publish("t", "hello");
        assertEquals(Optional.of("hello"), orch.getLatestValue("t", String.class));
        orch.publish("t", "world");
        assertEquals(Optional.of("world"), orch.getLatestValue("t", String.class));
    }

    @Test
    void publish_throwsOnNullValue() {
        orch.getOrCreateTopic("t", String.class);
        assertThrows(IllegalArgumentException.class, () -> orch.publish("t", null));
    }

    @Test
    void publish_autoCreatesTopic() {
        // Publishers don't need to pre-register topics; the topic is created
        // on first publish with the value's runtime type.
        assertEquals(Optional.empty(), orch.findTopic("late"));
        orch.publish("late", "hello");
        assertTrue(orch.findTopic("late").isPresent());
        assertEquals(Optional.of("hello"), orch.getLatestValue("late", String.class));
    }

    @Test
    void publish_throwsOnTypeMismatch() {
        orch.getOrCreateTopic("t", String.class);
        assertThrows(IllegalArgumentException.class, () -> orch.publish("t", 42));
    }

    @Test
    void primitiveAndWrapperTypesAreEquivalent() {
        // The library normalizes primitive vs wrapper so an annotation-bound
        // method with `double` and a programmatic topic created with `Double`
        // resolve to the same topic.
        Topic<Double> a = orch.getOrCreateTopic("t", Double.class);
        Topic<Double> b = orch.getOrCreateTopic("t", double.class);
        assertSame(a, b);

        orch.publish("t", 0.5);
        assertEquals(Optional.of(0.5), orch.getLatestValue("t", double.class));
        assertEquals(Optional.of(0.5), orch.getLatestValue("t", Double.class));
    }

    @Test
    void subscribe_receivesPublishedValues() throws Exception {
        orch.getOrCreateTopic("t", String.class);
        List<String> received = new ArrayList<>();
        orch.subscribe("t", String.class, received::add);

        orch.publish("t", "a");
        orch.publish("t", "b");

        // Callbacks are async; wait briefly.
        await(() -> received.size() >= 2);
        assertEquals(List.of("a", "b"), received);
    }

    @Test
    void subscribe_unsubscribe_stopsReceiving() throws Exception {
        orch.getOrCreateTopic("t", String.class);
        List<String> received = new ArrayList<>();
        Subscription sub = orch.subscribe("t", String.class, received::add);

        orch.publish("t", "a");
        await(() -> received.size() >= 1);
        assertEquals(1, received.size());

        sub.unsubscribe();
        orch.publish("t", "b");
        Thread.sleep(50);
        assertEquals(1, received.size(), "should not receive after unsubscribe");
    }
}
