package com.aaravlabs.pubsub.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be invoked on a fixed-rate loop at up to {@link #hz()} hertz.
 *
 * <p>Methods must take no parameters and return void. They run on the orchestrator's
 * dedicated scheduling thread pool, separate from the callback pool used to deliver
 * {@link SubscribedTo} callbacks.
 *
 * <p>The implementation uses fixed-delay scheduling: the next invocation starts
 * {@code 1000 / hz} milliseconds after the previous one <em>finishes</em>, so a slow
 * loop will not overlap itself.
 *
 * <p>Set {@link #hardware()} to {@code true} to route the loop to the orchestrator's
 * <b>single dedicated hardware thread</b>. Use this for periodic loops that read or
 * write FTC hardware — for example, a 50 Hz PID controller that sets motor power
 * every tick. See {@link OnHardwareThread} for the full rationale.
 *
 * <p>Example:
 * <pre>{@code
 * @RunPeriodically(hz = 50)
 * public void update() {
 *     drivetrain.drive(gamepad.left_stick_x, gamepad.right_stick_x);
 * }
 *
 * @RunPeriodically(hz = 50, hardware = true)
 * public void updatePID() {
 *     motor.setPower(pid.update(encoder.getCurrentPosition()));
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(RunPeriodicallys.class)
public @interface RunPeriodically {
    int hz() default 20;

    /**
     * If {@code true}, the loop runs on the orchestrator's single dedicated
     * hardware thread instead of the scheduler pool. All {@code hardware=true}
     * loops in the entire orchestrator share that one thread and execute
     * strictly serially.
     */
    boolean hardware() default false;
}
