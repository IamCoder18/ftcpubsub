# Synapse annotations reference

This file is the canonical table for each Synapse annotation. Cite source paths in parentheses when giving the user exact info.

## @SubscribedTo

- Package: `com.aaravlabs.synapse.annotation`
- Source: `src/main/java/com/aaravlabs/synapse/annotation/SubscribedTo.java`
- Target: method
- Attributes: `topic()` (required, string)
- Repeatable: yes (`@SubscribedTos`)
- Runtime contract: the binder subscribes a reflective handler; the handler runs on the **callback pool** unless `@OnHardwareThread` is also present.
- Method signature: takes 0 or 1 parameters. With one parameter, the type must match the topic (primitive ↔ wrapper normalized).
- Violation: a type mismatch throws `IllegalArgumentException` at first publish (see `OrchestratorImpl.java:212-215`).

```java
@SubscribedTo(topic = "intake/set/power")
@OnHardwareThread
public void setPower(double power) { motor.setPower(power); }
```

## @RunPeriodically

- Package: `com.aaravlabs.synapse.annotation`
- Source: `src/main/java/com/aaravlabs/synapse/annotation/RunPeriodically.java`
- Target: method
- Attributes: `hz()` (int, default 20), `hardware()` (boolean, default false)
- Repeatable: yes (`@RunPeriodicallys`)
- Runtime contract: scheduled with `scheduleWithFixedDelay`, so the next invocation starts after the previous one finishes. `hardware=true` routes to the single hardware thread.
- Method signature: takes no parameters, returns void.

```java
@RunPeriodically(hz = 50)
public void update() { /* math */ }

@RunPeriodically(hz = 50, hardware = true)
public void updatePID() { motor.setPower(pid.update(encoder.getCurrentPosition())); }
```

## @RunnableAction

- Package: `com.aaravlabs.synapse.annotation`
- Source: `src/main/java/com/aaravlabs/synapse/annotation/RunnableAction.java`
- Target: method
- Attributes: `value()` (required, string — the action name)
- Repeatable: no
- Runtime contract: registered into a name → method map. `orch.runAction("name")` returns a `CompletableFuture<Void>` that completes when the method returns.
- Method signature: takes no parameters, returns void.

```java
@RunnableAction("fire")
public void fire() {
    publish("gate/open", true);
    sleep(250);
    publish("gate/close", true);
}
```

## @OnHardwareThread

- Package: `com.aaravlabs.synapse.annotation`
- Source: `src/main/java/com/aaravlabs/synapse/annotation/OnHardwareThread.java`
- Target: method (typically combined with `@SubscribedTo`)
- Attributes: none
- Repeatable: no
- Runtime contract: the binder wraps the subscriber handler so it dispatches on the orchestrator's single hardware thread. Code in the method runs strictly serially with respect to every other piece of hardware-thread work in the orchestrator.
- What it does NOT do: it does not intercept ad-hoc hardware calls in non-annotated code, and it does not protect against `SafeOpMode.loop()` touching hardware directly.

```java
@SubscribedTo(topic = "intake/set/power")
@OnHardwareThread
public void setPower(double power) {
    HardwareThread.assertCurrent();   // optional defensive check
    motor.setPower(power);
}
```
