# Aarav PubSub for FTC

A tiny annotation-driven pub/sub bus for FTC robot code. Write `Node`s that talk to each
other through named, typed topics instead of direct references. The orchestrator manages
threads, so `@SubscribedTo` callbacks and `@RunPeriodically` loops never block each
other.

Designed to be installed like [Pedro Pathing](https://pedropathing.com) or
[Road Runner](https://github.com/acmerobotics/road-runner): a single Maven artifact
added to your `build.dependencies.gradle`.

## Installation

1. In your FTC project root, add the Aarav Maven repo + dependency:

   ```gradle
   // build.dependencies.gradle
   repositories {
       mavenLocal()       // if installing locally (see "Building from source" below)
       mavenCentral()
       google()
   }

   dependencies {
       implementation 'com.aarav:pubsub:0.1.0'
       // ... your other FTC deps
   }
   ```

2. In your OpMode, create one orchestrator and register your nodes:

   ```java
   public class MyTeleOp extends OpMode {
       private Orchestrator orch;

       @Override public void init() {
           orch = FtcOrchestrator.create();
           orch.getOrCreateTopic("intake/set/power", Double.class);

           orch.registerNode("intake", new IntakeNode(orch, hardwareMap));
           orch.registerNode("drivetrain", new DrivetrainNode(orch, hardwareMap));

           GamepadAdaptor.attach(orch, gamepad1, "gamepad1");

           orch.runAction("init");
       }

       @Override public void loop() {
           // 10 ms tick — OpModes don't need to do anything themselves anymore.
           try { Thread.sleep(10); } catch (InterruptedException ignored) {}
       }

       @Override public void stop() {
           orch.close();
       }
   }
   ```

## Concepts

### Topics

A topic is a named, typed channel. Create them with
`orch.getOrCreateTopic(name, type)` and publish to them with `orch.publish(name, value)`.

```java
orch.getOrCreateTopic("intake/set/power", Double.class);
orch.publish("intake/set/power", 0.8);
```

### Subscribing

Three ways, pick whichever fits:

```java
// 1) Programmatic — gives you a Subscription handle you can unsubscribe later.
orch.subscribe("intake/set/power", Double.class, p -> intake.setPower(p));

// 2) Annotation — declared on a Node method, wired automatically.
@SubscribedTo(topic = "intake/set/power")
public void onSetPower(double power) { intake.setPower(power); }

// 3) Fetch the latest value on demand (no subscription needed).
Optional<Double> latest = orch.getLatestValue("intake/set/power", Double.class);
```

### Periodic loops

```java
@RunPeriodically(hz = 50)  // default 20 Hz
public void update() {
    // Runs on the orchestrator's scheduler pool — never blocks subscribers.
}
```

### Named actions

```java
@RunnableAction("fire")
public void fire() {
    orch.publish("gate/open", true);
    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
    orch.publish("gate/close", true);
}

// From anywhere:
orch.runAction("fire");   // returns CompletableFuture<Void>
```

### Gamepad

```java
GamepadAdaptor.attach(orch, gamepad1, "gamepad1");
```

publishes these topics at 60 Hz:

| Topic                          | Type    | Meaning                              |
| ------------------------------ | ------- | ------------------------------------ |
| `gamepad1/<button>`            | Boolean | current state                        |
| `gamepad1/<button>/rising`     | Boolean | fires (value=true) on 0→1 transition |
| `gamepad1/<button>/falling`    | Boolean | fires on 1→0 transition              |
| `gamepad1/<axis>`              | Float   | current value                        |

So you can write:

```java
@SubscribedTo(topic = "gamepad1/right_bumper/rising")
public void startIntake() {
    orch.publish("intake/set/power", 1.0);
}

@SubscribedTo(topic = "gamepad1/right_bumper/falling")
public void stopIntake() {
    orch.publish("intake/set/power", 0.0);
}
```

## Why two thread pools?

The orchestrator runs:

- **Scheduler pool** (8 threads) for `@RunPeriodically` loops.
- **Callback pool** (4–16 threads, bounded queue of 256) for `@SubscribedTo` callbacks.
- **Action pool** (unbounded) for `@RunnableAction` invocations.
- **Hardware thread** (single dedicated thread) for any code that reads/writes
  FTC hardware. See the next section.

If a callback ever blocks (say, a slow `DcMotorEx` write), it cannot starve a periodic
loop — they run on separate pools. The callback pool uses
`CallerRunsPolicy` for backpressure: when the queue fills, the publisher
slows down instead of dropping messages.

## Hardware threading — the most important section

FTC hardware (`DcMotorEx`, servos, I2C sensors, the IMU, the `HardwareMap` itself)
is **not thread-safe**. Calling `setPower`, `setPosition`, or reading any sensor
from two threads at the same time produces race conditions, garbled serial-bus
responses, or `ConcurrentModificationException` crashes deep in the SDK.

The library gives you **three layers** of hardware-thread safety. Use whichever
matches your code style.

### Layer 1: Annotations on Node methods

```java
public class IntakeNode extends Node {
    private final DcMotorEx motor;

    public IntakeNode(Orchestrator orch, HardwareMap hwMap) {
        super(orch);
        this.motor = hwMap.get(DcMotorEx.class, "intake");
    }

    // Runs on the single hardware thread — safe to call motor.setPower().
    @SubscribedTo(topic = "intake/set/power")
    @OnHardwareThread
    public void setPower(double power) {
        motor.setPower(power);
    }

    // 50Hz PID controller; runs on the hardware thread.
    @RunPeriodically(hz = 50, hardware = true)
    public void updatePID() {
        motor.setPower(pid.update(motor.getCurrentPosition()));
    }
}
```

### Layer 2: `HardwareActions` facade (run / call / bulkRead)

When you want hardware-thread execution but you don't have a `@SubscribedTo`
callback handy — e.g. inside your `OpMode.init()` — use the `HardwareActions`
facade returned by `orchestrator.hardware()`:

```java
HardwareActions hw = orch.hardware();

// Async — returns immediately; work queued on the hardware thread.
hw.run(() -> intake.setPower(0.5));

// Sync — blocks the caller until the hardware thread completes; returns the value.
double pos = hw.call(() -> intake.getCurrentPosition());

// Async with a future — non-blocking.
CompletableFuture<Double> future = hw.callAsync(() -> intake.getCurrentPosition());

// Bulk read — runs a periodic reader on the hardware thread and lets it publish
// values to topics. Main thread reads from topics.
hw.bulkRead(50, view -> {
    double amps = intake.getCurrent(CurrentUnit.AMPS);
    view.publish("intake/amps", amps);
});
```

### Layer 3: `SafeDevice<T>` and `SafeHardwareMap`

The safest pattern: never call hardware methods directly. Wrap your hardware
lookups in a `SafeHardwareMap` and use the returned `SafeDevice` wrappers:

```java
@Override public void init() {
    orch = FtcOrchestrator.create();
    HardwareActions hw = orch.hardware();
    SafeHardwareMap safe = new SafeHardwareMap(hardwareMap, hw);

    SafeDevice<DcMotorEx> intake = safe.device(DcMotorEx.class, "intake");
    intake.run(m -> m.setPower(0.5));           // async, safe
    int pos = intake.call(DcMotorEx::getCurrentPosition);  // sync, safe

    // Or for many operations in a row, schedule them all at once:
    hw.run(() -> {
        DcMotorEx m = hardwareMap.get(DcMotorEx.class, "intake");
        m.setPower(0.5);
        m.setTargetPosition(1000);
        m.setMode(DcMotor.RunMode.RUN_TO_POSITION);
    });
}
```

### Recommended `SafeOpMode` pattern

A short base class you can drop into your project wraps the orchestrator lifecycle
and asserts your loop never runs on the hardware thread. Copy this into your
project:

```java
public abstract class SafeOpMode extends OpMode {
    protected Orchestrator orch;
    protected HardwareActions hardware;
    protected SafeHardwareMap safeMap;

    @Override public final void init() {
        orch = FtcOrchestrator.create();
        hardware = orch.hardware();
        safeMap = new SafeHardwareMap(hardwareMap, hardware);
        onSafeInit();
    }

    @Override public final void init_loop() { onSafeInitLoop(); }

    @Override public final void loop() {
        hardware.assertNotHardwareThread();   // throws if loop ever runs on hw thread
        hardware.tick();
        onSafeLoop();
    }

    @Override public final void start()  { onSafeStart(); }
    @Override public final void stop()   { orch.close(); }

    protected abstract void onSafeInit();
    protected void onSafeStart() {}
    protected void onSafeInitLoop() {}
    protected void onSafeLoop() {}
}
```

**Rules of thumb:**

1. Any method that touches `HardwareMap` or any device on it must be on the
   hardware thread (via `@OnHardwareThread`, `@RunPeriodically(hardware=true)`,
   or `HardwareActions.run/call`).
2. Don't touch hardware from `loop()`. Move that code into a Node with
   `@OnHardwareThread` or use `HardwareActions.run(...)` to schedule it.
3. Don't touch hardware from non-annotated `@SubscribedTo` callbacks.



## Project layout

```
pubsub/
├── settings.gradle
├── build.gradle                 ← publishes com.aarav:pubsub:0.1.0 to mavenLocal
└── src/
    ├── main/java/com/aarav/pubsub/
    │   ├── Orchestrator.java        ← public interface
    │   ├── OrchestratorImpl.java    ← default impl with two pools
    │   ├── Topic.java               ← typed channel + latest-value cache
    │   ├── Subscription.java        ← handle to unsubscribe
    │   ├── Node.java                ← base class
    │   ├── LogSink.java             ← pluggable logging
    │   ├── MessageHandler.java
    │   ├── annotation/
    │   │   ├── SubscribedTo.java
    │   │   ├── RunPeriodically.java
    │   │   └── RunnableAction.java
    │   ├── internal/
    │   │   └── AnnotationBinder.java  ← reflection scanner
    │   └── ftc/
    │       ├── FtcOrchestrator.java  ← factory that uses android.util.Log
    │       ├── AndroidLogSink.java
    │       └── GamepadAdaptor.java
    └── test/java/com/aarav/pubsub/
        ├── TopicTest.java
        ├── NodeTest.java
        ├── ActionTest.java
        └── ThreadingTest.java
```

## Building from source

```bash
cd pubsub
gradle test              # run JUnit tests
gradle publishToMavenLocal  # publishes to ~/.m2/repository/com/aarav/pubsub/0.1.0/
```

The library has zero runtime dependencies and zero dependencies on the FTC SDK — all
FTC-specific code (`android.util.Log`, `Gamepad`) is accessed via reflection, so the
core compiles and tests run on any plain JVM.

## Testing

18 unit tests, no FTC SDK required:

```bash
gradle test
```

Tests cover topic creation, latest-value cache, publish/subscribe mechanics,
annotation wiring, periodic scheduling, action invocation, and pool isolation.
