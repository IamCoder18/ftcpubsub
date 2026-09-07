package com.aaravlabs.synapse;

/**
 * Internal contract for what happens when a message arrives at a subscriber. The default
 * case wraps a plain {@link java.util.function.Consumer}; we keep it as an interface so
 * the orchestrator can special-case other shapes later (e.g. typed handlers) without
 * breaking the public API.
 */
@FunctionalInterface
public interface MessageHandler {
    void accept(Object message);
}
