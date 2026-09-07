# Migrating raw FTC → Synapse

Use this when the user has an existing OpMode using direct `DcMotorEx.setPower(...)` calls and wants to move it onto Synapse.

## Step 1 — Find every hardware write

Grep the OpMode (and any helper classes it calls) for:
- `setPower(`, `setPosition(`, `setVelocity(`
- `getCurrentPosition(`, `getCurrent(`
- `hardwareMap.get(`

Scan **all** OpMode lifecycle paths — `init()`, `init_loop()`, `start()`, `loop()`, `stop()`, and every helper reachable from them — not only `loop()` and `init_loop()`. Hardware writes can hide inside `start()` (e.g. resetting an encoder) or inside private methods called from `loop()`. Leaving any direct call outside the listed paths means the Synapse hardware thread is bypassed.

## Step 2 — Switch to `extends SafeOpMode`

Replace `extends OpMode` with `extends SafeOpMode` and rename `init()` → `onSafeInit()`, `loop()` → `onSafeLoop()`. You now have `orchestrator`, `hardware`, and `safeMap` ready.

## Step 3 — Wrap writes through `SafeDevice`

```java
SafeDevice<DcMotorEx> intake = safeMap.device(DcMotorEx.class, "intake");
intake.run(m -> m.setPower(0.5));                  // was: intake.setPower(0.5)
int pos = intake.call(DcMotorEx::getCurrentPosition); // was: intake.getCurrentPosition()
```

The `SafeDevice` automatically routes through the hardware thread.

## Step 4 — Extract a topic for each event source

For each gamepad button, axis, or sensor value you care about, pick a topic name and type:

```java
Topic<Double> intakeTarget = orchestrator.getOrCreateTopic("intake/target", Double.class);
```

Or skip the declaration and let `publish` create the topic from the value's runtime type.

## Step 5 — Build a Node

Take the per-subsystem code and put it in a `Node`:

```java
public class IntakeNode extends Node {
    IntakeNode(Orchestrator orch, SafeDevice<DcMotorEx> intake) {
        super(orch); this.intake = intake;
    }
    private final SafeDevice<DcMotorEx> intake;

    @SubscribedTo(topic = "intake/target")
    @OnHardwareThread
    public void onTarget(double power) {
        intake.run(m -> m.setPower(power));
    }
}
```

## Step 6 — Register the node

In `onSafeInit()`:

```java
orchestrator.registerNode("intake", new IntakeNode(orchestrator, intake));
```

Annotations bind at this point.

## Step 7 — Verify

Run `./gradlew test` on the Synapse repo (50+ tests), and run the OpMode in the FTC SDK simulator or on a real robot. Confirm the hardware-thread assertion in `SafeOpMode.loop()` never throws.
