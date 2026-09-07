package com.aaravlabs.synapse.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a subscriber to a topic. The method is invoked once for every
 * message published to that topic.
 *
 * <p>The method may take 0 or 1 parameters:
 * <ul>
 *   <li>{@code void onX()} — fires on every message, ignoring the value.</li>
 *   <li>{@code void onX(T msg)} — fires with the published value. The parameter type
 *       must match the topic's type (or be a supertype of it).</li>
 * </ul>
 *
 * <p>Repeating the annotation on a single method lets one method subscribe to multiple
 * topics.
 *
 * <p>Example:
 * <pre>{@code
 * @SubscribedTo(topic = "intake/set/power")
 * public void setIntakePower(double power) {
 *     intake.setPower(power);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(SubscribedTos.class)
public @interface SubscribedTo {
    String topic();
}
