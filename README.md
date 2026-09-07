# AaravLabs PubSub

[![CI](https://github.com/IamCoder18/ftcpubsub/actions/workflows/ci.yml/badge.svg)](https://github.com/IamCoder18/ftcpubsub/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Latest release](https://img.shields.io/github/v/tag/IamCoder18/ftcpubsub?label=release)](https://github.com/IamCoder18/ftcpubsub/releases)
[![Maven Package](https://img.shields.io/badge/Maven-GitHub%20Packages-blue)](https://github.com/IamCoder18/ftcpubsub/packages)

A tiny annotation-driven pub/sub bus for FTC robot code.

Write `Node`s that talk to each other through named, typed topics instead of
direct references. The orchestrator manages threads, so `@SubscribedTo`
callbacks and `@RunPeriodically` loops never block each other, and **all
hardware-touching code runs on a single dedicated thread** — the only safe
way to use `DcMotorEx`, servos, sensors, and bulk reads in an FTC program.

```java
@TeleOp(name = "Demo", group = "Test")
public class DemoOpMode extends SafeOpMode {
    @Override protected void onSafeInit() {
        SafeDevice<DcMotorEx> intake = safeMap.device(DcMotorEx.class, "intake");
        GamepadAdaptor.attach(orch, gamepad1, "g1");
    }

    @Override protected void onSafeLoop() {
        telemetry.addData("intake", orch.getLatestValue("intake/power", Double.class).orElse(0.0));
        telemetry.update();
    }

    public static class IntakeNode extends Node {
        IntakeNode(Orchestrator o, SafeDevice<DcMotorEx> intake) { super(o); this.intake = intake; }
        private final SafeDevice<DcMotorEx> intake;

        @SubscribedTo(topic = "g1/right_bumper/rising")
        @OnHardwareThread
        public void onPress(Boolean v) { intake.run(m -> m.setPower(1.0)); }

        @SubscribedTo(topic = "g1/right_bumper/falling")
        @OnHardwareThread
        public void onRelease(Boolean v) { intake.run(m -> m.setPower(0.0)); }
    }
}
```

## Features

- **Annotation-driven wiring** — `@SubscribedTo`, `@RunPeriodically`,
  `@RunnableAction`, `@OnHardwareThread`.
- **Hardware thread safety** — a dedicated single thread for all hardware I/O;
  runtime check in `SafeOpMode.loop()` makes accidental off-thread access fail
  fast.
- **Two-pool threading model** — separate scheduled and callback thread pools
  with blocking-queue backpressure so slow callbacks can't starve periodic
  loops and vice-versa.
- **`SafeDevice<T>` and `SafeHardwareMap`** — wrap any `HardwareMap` device so
  every method call routes through the hardware thread.
- **`HardwareActions`** facade — `run`, `call`, `callAsync`, `bulkRead` for
  ergonomic hardware access from anywhere.
- **`GamepadAdaptor`** — reflects the FTC SDK's `Gamepad` fields and publishes
  them to topics (`<gamepad>/<button>`, `/rising`, `/falling`, `/<axis>`)
  every 60 Hz.
- **Zero runtime dependencies** — the JAR is 40 KB with no transitive deps.
- **R8-minify-safe** — verified by running the full test suite through R8.

## Installation

Add this to `build.dependencies.gradle` in your FTC project:

```gradle
repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://maven.pkg.github.com/IamCoder18/ftcpubsub")
        credentials {
            username = findProperty("githubUser") ?: System.getenv("GITHUB_USER") ?: System.getenv("GITHUB_ACTOR")
            password = findProperty("githubToken") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'com.aaravlabs:pubsub:0.2.1'
    // ... your other FTC deps
}
```

GitHub Packages requires authentication on every download even for public
packages. Configure credentials via `~/.gradle/gradle.properties`:

```properties
githubUser=IamCoder18
githubToken=ghp_your_token_here
```

The token only needs `read:packages` scope.

## Quickstart

```java
@TeleOp(name = "PubsubDemo", group = "Demo")
public class PubsubDemo extends SafeOpMode {

    @Override
    protected void onSafeInit() {
        SafeDevice<DcMotorEx> left  = safeMap.device(DcMotorEx.class, "leftMotor");
        SafeDevice<DcMotorEx> right = safeMap.device(DcMotorEx.class, "rightMotor");

        orch.registerNode("drive", new DriveNode(left, right));
        GamepadAdaptor.attach(orch, gamepad1, "g1");
    }

    @Override
    protected void onSafeLoop() {
        telemetry.update();
    }

    public static class DriveNode extends Node {
        DriveNode(SafeDevice<DcMotorEx> left, SafeDevice<DcMotorEx> right) {
            super(/* your orchestrator */);
            this.left = left;
            this.right = right;
        }
        private final SafeDevice<DcMotorEx> left, right;

        @RunPeriodically(hz = 50, hardware = true)
        public void drive() {
            double y = orchestrator.getLatestValue("g1/left_stick_y", Double.class).orElse(0.0);
            double r = orchestrator.getLatestValue("g1/right_stick_y", Double.class).orElse(0.0);
            left.run(m -> m.setPower(-y));
            right.run(m -> m.setPower(-r));
        }
    }
}
```

## Documentation

- [Why two thread pools](#why-two-thread-pools) — explainer of the executor model.
- [Hardware threading](#hardware-threading--the-most-important-section) — the
  most important section to read before you ship.
- [`PubsubSmokeTest`](https://github.com/ATAARobotics/23684-Canopy-Biobuzz/blob/test/aaravlabs-pubsub/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/PubsubSmokeTest.java)
  in the biobuzz test worktree — a real working OpMode exercising every
  feature.
- [`CHANGELOG.md`](./CHANGELOG.md) — version history.

## Concepts

### Topics

A topic is a named, typed channel. Create with
`orch.getOrCreateTopic(name, type)`, publish with `orch.publish(name, value)`,
fetch with `orch.getLatestValue(name, type)`.

### Subscribing

Three ways, pick whichever fits:

```java
// 1) Programmatic — returns a Subscription you can unsubscribe later.
orch.subscribe("intake/set/power", Double.class, p -> intake.setPower(p));

// 2) Annotation — declared on a Node method, wired automatically.
@SubscribedTo(topic = "intake/set/power")
public void onSetPower(double power) { intake.setPower(power); }

// 3) Fetch the latest value on demand — no subscription needed.
Optional<Double> latest = orch.getLatestValue("intake/set/power", Double.class);
```

### Periodic loops

```java
@RunPeriodically(hz = 50)
public void update() { /* runs on the scheduler pool */ }

@RunPeriodically(hz = 50, hardware = true)
public void update() { motor.setPower(...); /* runs on the hardware thread */ }
```

### Hardware-thread API

```java
hw.run(() -> motor.setPower(0.5));
double pos = hw.call(() -> motor.getCurrentPosition());
CompletableFuture<Double> future = hw.callAsync(() -> motor.getCurrentPosition());

hw.bulkRead(50, view -> {
    double amps = motor.getCurrent(CurrentUnit.AMPS);
    view.publish("motor/amps", amps);
});
```

### Gamepad

`GamepadAdaptor.attach(orch, gamepad1, "g1")` publishes:

| Topic                  | Type    | Meaning                              |
| ---------------------- | ------- | ------------------------------------ |
| `g1/<button>`          | Boolean | current state                        |
| `g1/<button>/rising`   | Boolean | fires (value=true) on 0→1 transition |
| `g1/<button>/falling`  | Boolean | fires on 1→0 transition              |
| `g1/<axis>`            | Float   | current value                        |

## Why two thread pools?

The orchestrator runs:

- **Scheduler pool** (8 threads) for `@RunPeriodically` loops.
- **Callback pool** (4–16 threads, bounded queue of 256) for `@SubscribedTo`
  callbacks.
- **Action pool** (unbounded) for `@RunnableAction` invocations.
- **Hardware thread** (single dedicated thread) for any code that
  reads/writes FTC hardware.

If a callback ever blocks (say, a slow `DcMotorEx` write), it cannot starve a
periodic loop — they run on separate pools. The callback pool uses
`CallerRunsPolicy` for backpressure: when the queue fills, the publisher slows
down instead of dropping messages.

A third, unbounded pool handles `@RunnableAction` invocations so a long action
can't be rejected.

## Hardware threading — the most important section

> [!IMPORTANT]
> FTC hardware is **not thread-safe**. Calling `setPower`, `setPosition`, or
> reading any sensor from two threads at the same time produces race
> conditions, garbled serial-bus responses, or
> `ConcurrentModificationException` crashes deep in the SDK.

The library gives you **three layers** of hardware-thread safety. Use
whichever matches your code style.

### Layer 1: Annotations on Node methods

```java
@SubscribedTo(topic = "intake/set/power")
@OnHardwareThread
public void setPower(double power) {
    HardwareThread.assertCurrent();   // optional defensive check
    motor.setPower(power);
}

@RunPeriodically(hz = 50, hardware = true)
public void updatePID() {
    motor.setPower(pid.update(motor.getCurrentPosition()));
}
```

### Layer 2: `HardwareActions` facade

```java
HardwareActions hw = orch.hardware();

hw.run(() -> intake.setPower(0.5));
double pos = hw.call(() -> intake.getCurrentPosition());
hw.bulkRead(50, view -> {
    view.publish("intake/amps", intake.getCurrent(CurrentUnit.AMPS));
});
```

### Layer 3: `SafeDevice<T>` and `SafeHardwareMap`

```java
SafeHardwareMap safe = new SafeHardwareMap(hardwareMap, hw);
SafeDevice<DcMotorEx> intake = safe.device(DcMotorEx.class, "intake");
intake.run(m -> m.setPower(0.5));
int pos = intake.call(DcMotorEx::getCurrentPosition);
```

### Drop-in `SafeOpMode` template

A short base class for your project:

```java
public abstract class SafeOpMode extends OpMode {
    protected Orchestrator orch;
    protected HardwareActions hardware;
    protected SafeHardwareMap safeMap;

    @Override public final void init() {
        orch = com.aaravlabs.pubsub.ftc.FtcOrchestrator.create();
        hardware = orch.hardware();
        safeMap = new SafeHardwareMap(hardwareMap, hardware);
        onSafeInit();
    }

    @Override public final void loop() {
        hardware.assertNotHardwareThread();   // fails fast if you broke the rule
        onSafeLoop();
    }

    @Override public final void stop() { orch.close(); }

    protected abstract void onSafeInit();
    protected void onSafeLoop() {}
}
```

## Contributing

Issues and PRs welcome. See [CONTRIBUTING.md](./CONTRIBUTING.md) for the
workflow, [CONDUCT.md](./CODE_OF_CONDUCT.md) for the code of conduct, and
[CHANGELOG.md](./CHANGELOG.md) for the version history.

## License

[MIT](./LICENSE) © 2026 AaravLabs
