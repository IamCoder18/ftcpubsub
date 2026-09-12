# Migrating raw FTC → Synapse

Use when an existing OpMode (or whole codebase) uses direct `DcMotorEx`/`Servo`/`IMU` calls. Migrate **subsystem by subsystem**, verifying on the bench each time — not one big-bang rewrite. This is the exact sequence used on the 23684 Canopy-Biobuzz robot.

## Step 1 — Find every hardware touch

Grep the OpMode and every helper it calls for the full set:

```text
setPower(        setPosition(     setVelocity(     setDirection(
setMode(         setTargetPosition(
getCurrentPosition(               getCurrent(      getVelocity(
isBusy(          atTarget(        getRobotYawPitchRollAngles(
hardwareMap.get(
```

Scan **all** lifecycle paths — `init()`, `init_loop()`, `start()`, `loop()`, `stop()`, and every private method reachable from them. Hardware writes hide inside `start()` (encoder resets) and inside helpers called from `loop()`. Every hit is a call site that must route through the hardware thread by the end of the migration.

## Step 2 — Switch the base class

```text
extends OpMode   →   extends SafeOpMode
init()           →   onSafeInit()        (required)
init_loop()      →   onSafeInitLoop()
start()          →   onSafeStart()
loop()           →   onSafeLoop()
stop()           →   onSafeStop()
```

`orchestrator`, `hardware`, and `safeMap` are now fields. `telemetry`, `gamepad1/2`, and `hardwareMap` still work in the hooks (OpMode thread) — only raw device calls are now forbidden there.

## Step 3 — Wrap each device

```java
// was: DcMotorEx intake = hardwareMap.get(DcMotorEx.class, "intake");
SafeDevice<DcMotorEx> intake = safeMap.device(DcMotorEx.class, "intake");
```

Mechanical call-site rewrites:

```java
// was: intake.setPower(0.5);
intake.run(m -> m.setPower(0.5));

// was: int pos = intake.getCurrentPosition();
int pos = intake.call(DcMotorEx::getCurrentPosition);

// was: intake.setDirection(DcMotor.Direction.REVERSE);   (in init paths)
intake.run(m -> m.setDirection(DcMotor.Direction.REVERSE));
```

**This step alone makes the existing OpMode hardware-thread-safe.** It compiles and behaves identically — land it before touching architecture.

## Step 4 — Name the topics

Before extracting nodes, fix the topic surface:

- one root per producer: `g1/...`, `g2/...` (via `GamepadAdaptor.attach`), `drive/...`, `intake/...`, `shooter/...`;
- per-wheel / per-axis state that telemetry needs gets its own topic (`drive/power/fl`), so the OpMode never reads devices for display;
- declare shared topics explicitly with `getOrCreateTopic(name, Type.class)` at the top of the owning node — the first publish otherwise locks the type from the value's runtime class.

## Step 5 — Extract one subsystem into a Node

Lift the per-subsystem code out of `loop()`. Inputs become `getLatestValue`; outputs become `SafeDevice` writes plus `publish` of state:

```java
public class Intake extends Node {

    private final SafeDevice<DcMotorEx> intake;

    public Intake(Orchestrator orch, SafeDevice<DcMotorEx> intake) {
        super(orch);
        this.intake = intake;
    }

    @RunPeriodically(hz = 50, hardware = true)
    public void runIntake() {
        double operator = -orchestrator.getLatestValue("g2/left_stick_y", Float.class).map(Float::doubleValue).orElse(0.0);
        double trigger = orchestrator.getLatestValue("g1/left_trigger", Float.class).map(Float::doubleValue).orElse(0.0);
        double power = Math.max(operator, trigger);
        intake.run(m -> m.setPower(power));
        orchestrator.publish("intake/power", power);
    }
}
```

Event-driven logic (button edges, setpoints) becomes `@SubscribedTo` methods with `@OnHardwareThread` when they touch devices. One concern per node; devices via constructor; never reference `hardwareMap` from a node.

## Step 6 — Register nodes in onSafeInit

```java
@Override
protected void onSafeInit() {
    frontLeft = safeMap.device(DcMotorEx.class, "frontLeft");
    // ... remaining devices ...
    GamepadAdaptor.attach(orchestrator, gamepad1, "g1");
    GamepadAdaptor.attach(orchestrator, gamepad2, "g2");

    orchestrator.registerNode("drive", new Drive(orchestrator, frontLeft, frontRight, backLeft, backRight));
    orchestrator.registerNode("intake", new Intake(orchestrator, intake));
}
```

Binding happens here: subscriptions, loops, and actions all wire up at `registerNode`. Node names are unique — duplicates silently skip (WARN in Logcat), so match names to subsystem files.

## Step 7 — Verify on the robot

Migration is not done until verified:

1. **Bench, wheels up** — run the direction test (see `references/recipes.md`); every motor spins correctly.
2. **Driver Station** — OpMode listed, `onSafeInit` clean in Logcat, telemetry values move.
3. **Driving** — controls map correctly; no `IllegalStateException`.
4. **Logcat check** — no WARN lines about duplicate node/action registrations, no "topic is typed" exceptions.

Checklist used on the reference robot: wired correctly · runs without errors · telemetry correct · motors/servos respond · controls mapped · verified bench **and** field.

## Rules that keep the migration honest

- **Zero raw call sites remain.** Every hit from Step 1 must be rewritten in Step 3. One missed call site reintroduces the race the migration exists to fix.
- **Never create a second orchestrator.** `SafeOpMode` owns the lifecycle; `Orchestrator.create(...)` inside an OpMode duplicates pools and breaks cleanup.
- **Never block on the hardware thread.** Inside `hardware = true` loops / `@OnHardwareThread` subscribers, use `device.raw()` — `call(...)` there deadlocks (see `references/safety.md`).
- **One subsystem at a time**, verified on the bench between extractions.
