---
name: synapse-pubsub-ftc
description: Builds FIRST Tech Challenge (FTC) robot code on the Synapse annotation-driven pub/sub bus. Use when the user asks for FTC robot code, hardware-thread-safe teleop or autonomous logic, refactoring raw FTC OpModes onto Synapse, or mentions Synapse, synapse-pubsub, @SubscribedTo, @RunPeriodically, @OnHardwareThread, SafeOpMode, HardwareActions, SafeDevice, GamepadAdaptor, or the Synapse orchestrator.
---

# Synapse on FTC

Synapse is a tiny Java library (FTC-compatible Maven coords `com.aaravlabs:synapse`) that gives FTC robot code a single dedicated hardware thread and a typed pub/sub bus. This skill is the canonical reference for writing and reviewing Synapse-based OpModes.

## When to use

- Writing a NEW FTC OpMode that should use Synapse primitives.
- Migrating an existing OpMode off direct `DcMotorEx` / `Servo` / `IMU` calls onto the hardware thread.
- Debugging a race condition or repeated `RuntimeException` involving FTC hardware.
- Wiring gamepads, sensors, or actuators via topics.

## When NOT to use

- Pure FRC / non-FTC code. STOP and recommend the FTC analogue.
- Tasks that don't involve hardware (vision pipelines, UI) — Synapse still works but is overkill.

## Mental model

- One `Orchestrator` per robot. It owns four executors: scheduler, callback pool, action pool, and a **single** hardware thread.
- Annotations are the entry point. Classes extend `Node` and methods are bound by the annotation binder at `registerNode` time.
- All hardware calls must cross the hardware thread. Off-thread access throws.
- Topics are typed (`Topic<Double>`, `Topic<Boolean>`). Subscribers get type-checked callbacks.

## Core workflow (checklist)

```
- [ ] 1. Confirm user wants Synapse (not raw FTC).
- [ ] 2. Identify OpMode lifecycle hooks needed (init / init_loop / loop / stop).
- [ ] 3. Decide the Topics (name + Java type) BEFORE writing nodes.
- [ ] 4. Build Node classes (one concern each: drive, intake, shooter, etc.).
- [ ] 5. Wire hardware via safeMap.device(...) and SafeDevice.run(...) — never raw.
- [ ] 6. Validate hardware-thread discipline with hardware.assertNotHardwareThread() in onSafeLoop.
- [ ] 7. Provide a copy-paste MyFirstOpMode extends SafeOpMode if the user is new.
```

## Decision trees

### "Should this run on the hardware thread?"

- Touches any `DcMotor*`, `Servo*`, `IMU`, `ColorSensor`, `TouchSensor`, `DigitalChannel` → YES via `@OnHardwareThread` or `hardware.call(...)`.
- Reads gamepad input → NO (gamepad reads are off-thread safe and update via `GamepadAdaptor`).
- Pure math / state machine → NO.

### "Periodic vs action vs subscription?"

- Needs to fire every N ms regardless of input → `@RunPeriodically(hz=N)`.
- One-shot in response to a button → `@RunnableAction("name")` + `orchestrator.runAction("name")`.
- Reactive to a state change → `@SubscribedTo("topic")`.

### "Topic type — primitive vs wrapper?"

- Either works. `double` and `Double` are interchangeable at the topic layer (see the `boxed(Class)` helper). Prefer primitives in annotations.

## Patterns

[Use this template when generating a new node]

```java
public class DriveNode extends Node {
    public DriveNode(Orchestrator orchestrator, SafeDevice<DcMotorEx> left, SafeDevice<DcMotorEx> right) {
        super(orchestrator);
        // Registration happens from the owning OpMode, not from the node's
        // own constructor — see Common mistake #2.
    }

    @SubscribedTo("drive/target")
    void onTarget(TargetPose t) {
        // hardware-thread work uses HardwareActions or @OnHardwareThread.
    }
}

// In the OpMode's onSafeInit():
// orchestrator.registerNode("drive", new DriveNode(orchestrator, left, right));
```

## Common mistakes

1. Calling `motor.setPower(...)` from `onSafeLoop()` → invalid-thread failure (the call runs on the OpMode thread, not the hardware thread). Use `hardware.run(...)` or `@OnHardwareThread`.
2. Registering the node without a stable name → `Orchestrator.registerNode(String, Node)` requires both. Call it from the owning OpMode, e.g. `orchestrator.registerNode("drive", node)`. Constructor-side `registerNode(this)` does not compile.
3. Using an `int` topic for a sensor that produces `double` → type mismatch at publish time.
4. Re-creating an `Orchestrator` per `loop()` call → memory leak and thread churn.

## Reference files (load on demand)

- `references/annotations.md` — full attribute table for each annotation.
- `references/safety.md` — the four safety invariants and which tests prove each.
- `references/recipes.md` — copy-paste recipes: mecanum, bulk reads, two-controller teleop.
- `references/migration.md` — step-by-step raw-FTC → Synapse refactor.
