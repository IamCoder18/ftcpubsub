package com.aaravlabs.pubsub.ftc;

import com.aaravlabs.pubsub.Orchestrator;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Functional interface for bulk-read callbacks. The single abstract method
 * {@code read(HardwareView)} is invoked on the hardware thread at a fixed
 * rate, and receives a {@link HardwareView} the user uses to publish values
 * to topics.
 *
 * <p>Example:
 * <pre>{@code
 * hw.bulkRead(50, view -> {
 *     int pos = motor.getCurrentPosition();
 *     double amps = motor.getCurrent(CurrentUnit.AMPS);
 *     view.publish("motor/pos", pos);
 *     view.publish("motor/amps", amps);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface BulkReader {
    void read(HardwareView view);
}
