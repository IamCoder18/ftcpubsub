package com.aaravlabs.pubsub.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a zero-arg method as a named {@code RunnableAction} that can be fired by
 * name via {@code Orchestrator.runAction(name)} (returning a {@link
 * java.util.concurrent.CompletableFuture} that completes when the action returns).
 *
 * <p>Actions run on a dedicated unbounded thread pool separate from the callback and
 * periodic-loop pools, so a long-running action does not block the bus.
 *
 * <p>Example:
 * <pre>{@code
 * @RunnableAction("fire")
 * public void fire() {
 *     publish("gate/open", true);
 *     sleep(250);
 *     publish("gate/close", true);
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RunnableAction {
    String value();
}
