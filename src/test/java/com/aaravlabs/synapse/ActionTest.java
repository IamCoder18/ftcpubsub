package com.aaravlabs.synapse;

import com.aaravlabs.synapse.annotation.RunnableAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ActionTest {

    private Orchestrator orchestrator;

    @BeforeEach void setUp() { orchestrator = Orchestrator.create("test"); }
    @AfterEach  void tearDown() { orchestrator.close(); }

    static class ActionNode extends Node {
        final AtomicInteger calls = new AtomicInteger();
        ActionNode(Orchestrator o) { super(o); }
        @RunnableAction("fire")
        public void fire() { calls.incrementAndGet(); }
    }

    static class BadActionNode extends Node {
        BadActionNode(Orchestrator o) { super(o); }
        @RunnableAction("explode")
        public int explode() { return 99; }
    }

    @Test
    void runAction_completesFuture() throws Exception {
        ActionNode n = new ActionNode(orchestrator);
        orchestrator.registerNode("a", n);

        CompletableFuture<Void> f = orchestrator.runAction("fire");
        f.get(1, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, n.calls.get());
    }

    @Test
    void runAction_unknownName_failsFast() {
        CompletableFuture<Void> f = orchestrator.runAction("nope");
        assertTrue(f.isCompletedExceptionally());
    }

    @Test
    void runAction_rejectsMethodsWithReturnType() {
        BadActionNode n = new BadActionNode(orchestrator);
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.registerNode("a", n));
    }
}
