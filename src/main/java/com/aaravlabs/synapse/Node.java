package com.aaravlabs.synapse;

import com.aaravlabs.synapse.internal.AnnotationBinder;

/**
 * Base class for units of independent robot logic. Each node is registered with an
 * {@link Orchestrator} via {@link Orchestrator#registerNode(String, Node)}, which
 * scans the node for annotated methods and wires them up automatically:
 *
 * <ul>
 *   <li>{@link com.aaravlabs.synapse.annotation.SubscribedTo} on methods — invoked when
 *       their topic receives a message.</li>
 *   <li>{@link com.aaravlabs.synapse.annotation.RunPeriodically} on methods — invoked on
 *       a fixed-rate loop.</li>
 *   <li>{@link com.aaravlabs.synapse.annotation.RunnableAction} on methods — registered
 *       as a named action that can be fired later.</li>
 * </ul>
 *
 * <p>Subclasses should call {@code super(orchestrator)} from their constructor.
 *
 * <p>Example:
 * <pre>{@code
 * public class Drivetrain extends Node {
 *     public Drivetrain(Orchestrator orch) {
 *         super(orch);
 *     }
 *
 *     @SubscribedTo(topic = "drive/set")
 *     public void onDriveSet(DriveSignal s) {
 *         // apply to motors
 *     }
 *
 *     @RunPeriodically(hz = 50)
 *     public void update() {
 *         // periodic loop
 *     }
 * }
 * }</pre>
 */
public abstract class Node implements AutoCloseable {

    protected final Orchestrator orchestrator;

    /**
     * Create a node bound to the given orchestrator. Every subclass constructor must
     * call {@code super(orchestrator)}.
     *
     * @param orchestrator the bus this node publishes to and reads from
     */
    protected Node(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Override to release resources when the node is unregistered. The default
     * implementation is a no-op; {@link AnnotationBinder} handles cleanup of
     * subscriptions and scheduled tasks automatically.
     */
    @Override
    public void close() {
        // no-op by default
    }
}
