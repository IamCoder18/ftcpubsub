package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Orchestrator;

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

    /**
     * Read hardware state and publish it. Runs on the hardware thread.
     *
     * @param view used to publish readings onto the bus
     */
    void read(HardwareView view);
}
