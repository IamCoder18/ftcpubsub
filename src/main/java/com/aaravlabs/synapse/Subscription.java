package com.aaravlabs.synapse;

/**
 * Handle to an active subscription. Call {@link #unsubscribe()} to stop receiving
 * messages on the topic.
 */
public final class Subscription {

    private final Topic<?> topic;
    private final MessageHandler handler;
    private final OrchestratorImpl orchestrator;

    Subscription(Topic<?> topic, MessageHandler handler, OrchestratorImpl orchestrator) {
        this.topic = topic;
        this.handler = handler;
        this.orchestrator = orchestrator;
    }

    /**
     * Stop receiving messages. Idempotent.
     */
    public void unsubscribe() {
        orchestrator.removeSubscription(this);
    }

    /**
     * @return the topic this subscription is bound to
     */
    Topic<?> topic() {
        return topic;
    }

    /**
     * @return the handler registered with this subscription
     */
    MessageHandler handler() {
        return handler;
    }
}
