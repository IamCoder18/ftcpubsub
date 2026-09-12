package com.aaravlabs.synapse;

import java.util.Optional;

/**
 * A typed pub/sub channel. Topics are addressed by string name and typed by
 * {@link Class}. Each topic remembers the most recent value published to it so that
 * nodes can {@link #latestValue()} it on demand instead of being forced to subscribe.
 *
 * <p>Topics are created by an {@link Orchestrator} via
 * {@link Orchestrator#getOrCreateTopic(String, Class)} — you should not construct them
 * directly.
 *
 * @param <T> the message type carried by this topic
 */
public final class Topic<T> {

    private final String name;
    private final Class<T> type;

    // Guarded by `this` for write, volatile for the read in publish().
    private volatile T latest;
    private volatile long latestPublishNanos;

    Topic(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    /**
     * @return the topic name
     */
    public String name() {
        return name;
    }

    /**
     * @return the topic's message type
     */
    public Class<T> type() {
        return type;
    }

    /**
     * The most recently published value, or empty if nothing has been published yet.
     *
     * @return an {@link Optional} holding the latest value
     */
    public synchronized Optional<T> latestValue() {
        return Optional.ofNullable(latest);
    }

    /**
     * Wall-clock nanos at which {@link #latestValue()} was last updated.
     *
     * @return publish timestamp in nanoseconds ({@code System.nanoTime()} clock)
     */
    public synchronized long latestPublishNanos() {
        return latestPublishNanos;
    }

    /**
     * Record a new latest value. Called by the orchestrator immediately before notifying
     * subscribers.
     *
     * @param value the value to record
     */
    synchronized void recordLatest(T value) {
        this.latest = value;
        this.latestPublishNanos = System.nanoTime();
    }

    /**
     * Returns the most recent value, falling back to {@code defaultValue} if nothing has
     * been published yet. Convenience for {@code topic.latestValue().orElse(default)}.
     *
     * @param defaultValue the value to return before the first publish
     * @return the latest value, or {@code defaultValue}
     */
    public T latestValueOr(T defaultValue) {
        T v = latest;
        return v != null ? v : defaultValue;
    }

    @Override
    public String toString() {
        return "Topic[" + name + ":" + type.getSimpleName() + "]";
    }
}
