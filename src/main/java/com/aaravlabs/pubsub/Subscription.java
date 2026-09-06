package com.aaravlabs.pubsub;

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

    /** Stop receiving messages. Idempotent. */
    public void unsubscribe() {
        orchestrator.removeSubscription(this);
    }

    Topic<?> topic() {
        return topic;
    }

    MessageHandler handler() {
        return handler;
    }
}
