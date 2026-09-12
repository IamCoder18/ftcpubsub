package com.aaravlabs.synapse.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a zero-arg, void-returning (non-static) method as a named action that can
 * be fired by name via {@code Orchestrator.runAction(name)}. The returned {@link
 * java.util.concurrent.CompletableFuture} completes when the method returns normally,
 * or completes exceptionally if it throws or no action with that name exists.
 *
 * <p>Actions run on a dedicated unbounded thread pool separate from the callback and
 * periodic-loop pools, so a long-running action does not block the bus. The action
 * pool is <b>not</b> the hardware thread: an action that touches hardware must route
 * its hardware work through {@code HardwareActions} or {@code SafeDevice}.
 *
 * <p>Action names are global. Registering two actions with the same name keeps the
 * first and logs a warning about the second.
 *
 * <p>Example:
 * <pre>{@code
 * @RunnableAction("fire")
 * public void fire() {
 *     orchestrator.publish("gate/open", true);
 *     sleep(250);
 *     orchestrator.publish("gate/close", true);
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RunnableAction {
    String value();
}
