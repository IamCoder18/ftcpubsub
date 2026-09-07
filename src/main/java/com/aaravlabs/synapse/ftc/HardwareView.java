package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Orchestrator;

/**
 * Read-side companion to {@link HardwareActions#bulkRead(int, BulkReader)}.
 * Provides convenient {@code publish} and {@code getLatestValue} methods that
 * the bulk-read callback uses to record hardware readings onto the bus.
 *
 * <p>Instances are created per-callback by {@code HardwareActions.bulkRead}; you
 * should not construct them yourself.
 */
public final class HardwareView {

    private final Orchestrator orchestrator;

    public HardwareView(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Publish a value from inside a bulk-read callback. The publish itself is
     * fast (topic cache + non-blocking enqueue). Safe to call at high frequency.
     */
    public <T> void publish(String topic, T value) {
        orchestrator.publish(topic, value);
    }

    /**
     * Read the most recently published value on a topic. Convenience for
     * "publish the latest reading from another sensor" patterns.
     */
    public <T> java.util.Optional<T> getLatestValue(String topic, Class<T> type) {
        return orchestrator.getLatestValue(topic, type);
    }
}
