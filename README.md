# Synapse

[![Test](https://github.com/IamCoder18/synapse/actions/workflows/test.yml/badge.svg)](https://github.com/IamCoder18/synapse/actions/workflows/test.yml)
[![Publish](https://github.com/IamCoder18/synapse/actions/workflows/publish.yml/badge.svg)](https://github.com/IamCoder18/synapse/actions/workflows/publish.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Latest release](https://img.shields.io/github/v/tag/IamCoder18/synapse?label=release)](https://github.com/IamCoder18/synapse/releases)
[![Maven Package](https://img.shields.io/badge/Maven-GitHub%20Packages-blue)](https://github.com/IamCoder18/synapse/packages)

A tiny, annotation-driven pub/sub bus for **FIRST** Tech Challenge robot code.

Write `Node`s that talk to each other through named, typed **topics** instead
of direct references. The orchestrator manages threads, so `@SubscribedTo`
callbacks and `@RunPeriodically` loops never block each other, and **all
hardware-touching code runs on a single dedicated thread** — the only safe
way to use `DcMotorEx`, servos, sensors, and bulk reads in an FTC program.

> 40 KB JAR. Zero runtime dependencies. R8-minify-safe.

```java
@TeleOp(name = "Demo", group = "Test")
public class DemoOpMode extends SafeOpMode {
    @Override protected void onSafeInit() {
        SafeDevice<DcMotorEx> intake = safeMap.device(DcMotorEx.class, "intake");
        GamepadAdaptor.attach(orchestrator, gamepad1, "g1");
    }

    @Override protected void onSafeLoop() {
        telemetry.addData("intake", orchestrator.getLatestValue("intake/power", Double.class).orElse(0.0));
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

## Table of contents

- [Why Synapse?](#why-synapse)
- [Why pub/sub for FTC?](#why-pubsub-for-ftc)
- [Quickstart](#quickstart)
- [Concepts](#concepts)
- [Hardware threading — the most important section](#hardware-threading--the-most-important-section)
- [Threading model](#threading-model)
- [Installation](#installation)
- [API reference](#api-reference)
- [Testing & development](#testing--development)
- [Contributing](#contributing)
- [License](#license)

## Why Synapse?

Pub/sub for FTC isn't new — [Heron Robotics](https://github.com/HeronRobotics/heron)
showed what it could do for an 18-ball autonomous, and their architecture
inspired this one. Synapse takes the same idea and makes it the **safest and
smallest** library in the niche:

- **One hardware thread, no exceptions.** Every `DcMotorEx`, `Servo`, and
  sensor I/O — whether triggered by a gamepad event, a periodic loop, or a
  bulk read — funnels through a single dedicated thread. A runtime assertion
  in `SafeOpMode.loop()` makes off-thread hardware access fail fast, not
  silently corrupt your I²C bus.
- **Three-layer API.** Annotations (`@SubscribedTo`, `@RunPeriodically`,
  `@RunnableAction`), a `HardwareActions` facade (`run`, `call`, `callAsync`,
  `bulkRead`), and `SafeDevice<T>` wrappers. Use whichever matches your
  style.
- **Backpressure that respects deadlines.** Separate pools for periodic and
  callback work, with a bounded queue and `CallerRunsPolicy` so a slow
  subscriber slows the publisher instead of dropping messages. An unbounded
  action pool keeps one-shot invocations from being rejected under load.
- **Zero runtime dependencies.** 40 KB JAR. R8 survival is verified by
  running the test suite through minification.
- **Gamepad-to-topic in one line.** `GamepadAdaptor.attach(orch, gamepad1, "g1")`
  publishes buttons (current, rising, falling) and axes at 60 Hz.

## Why pub/sub for FTC?

Traditional command-based FTC code runs every subsystem on a single loop.
It works, but the only way to express "run my intake only when the bumper
state changes" and "update my LEDs at 10 Hz" in the same loop is to count
ticks manually and hope nothing else is dragging the loop down.

In a pub/sub architecture, those two requirements become two independent
subscriptions that the orchestrator schedules on separate pools. A slow
callback can't starve your periodic loop, and vice-versa — and any of them
can opt in to running on the dedicated hardware thread.

## Quickstart

```java
@TeleOp(name = "SynapseDemo", group = "Demo")
public class SynapseDemo extends SafeOpMode {

    @Override
    protected void onSafeInit() {
        SafeDevice<DcMotorEx> left  = safeMap.device(DcMotorEx.class, "leftMotor");
        SafeDevice<DcMotorEx> right = safeMap.device(DcMotorEx.class, "rightMotor");

        orchestrator.registerNode("drive", new DriveNode(orchestrator, left, right));
        GamepadAdaptor.attach(orchestrator, gamepad1, "g1");
    }

    @Override
    protected void onSafeLoop() {
        telemetry.update();
    }

    public static class DriveNode extends Node {
        DriveNode(Orchestrator orch, SafeDevice<DcMotorEx> left, SafeDevice<DcMotorEx> right) {
            super(orch);
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

## Concepts

### Topics

A topic is a named, typed channel. Create with
`orchestrator.getOrCreateTopic(name, type)`, publish with
`orchestrator.publish(name, value)`, fetch with
`orchestrator.getLatestValue(name, type)`.

### Subscribing

Three ways, pick whichever fits:

```java
// 1) Programmatic — returns a Subscription you can unsubscribe later.
orchestrator.subscribe("intake/set/power", Double.class, p -> intake.setPower(p));

// 2) Annotation — declared on a Node method, wired automatically.
@SubscribedTo(topic = "intake/set/power")
public void onSetPower(double power) { intake.setPower(power); }

// 3) Fetch the latest value on demand — no subscription needed.
Optional<Double> latest = orchestrator.getLatestValue("intake/set/power", Double.class);
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
HardwareActions hw = orchestrator.hardware();

hw.run(() -> motor.setPower(0.5));
double pos = hw.call(() -> motor.getCurrentPosition());
CompletableFuture<Double> future = hw.callAsync(() -> motor.getCurrentPosition());

hw.bulkRead(50, view -> {
    double amps = motor.getCurrent(CurrentUnit.AMPS);
    view.publish("motor/amps", amps);
});
```

### Gamepad

`GamepadAdaptor.attach(orchestrator, gamepad1, "g1")` publishes:

| Topic                  | Type    | Meaning                              |
| ---------------------- | ------- | ------------------------------------ |
| `g1/<button>`          | Boolean | current state                        |
| `g1/<button>/rising`   | Boolean | fires (value=true) on 0→1 transition |
| `g1/<button>/falling`  | Boolean | fires on 1→0 transition              |
| `g1/<axis>`            | Float   | current value                        |

## Hardware threading — the most important section

> [!IMPORTANT]
> FTC hardware is **not thread-safe**. Calling `setPower`, `setPosition`, or
> reading any sensor from two threads at the same time produces race
> conditions, garbled serial-bus responses, or
> `ConcurrentModificationException` crashes deep in the SDK.

Synapse gives you **three layers** of hardware-thread safety. Use whichever
matches your code style.

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
HardwareActions hw = orchestrator.hardware();

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

```java
public abstract class SafeOpMode extends OpMode {
    protected Orchestrator orchestrator;
    protected HardwareActions hardware;
    protected SafeHardwareMap safeMap;

    @Override public final void init() {
        orchestrator = com.aaravlabs.synapse.ftc.FtcOrchestrator.create();
        hardware = orchestrator.hardware();
        safeMap = new SafeHardwareMap(hardwareMap, hardware);
        onSafeInit();
    }

    @Override public final void loop() {
        hardware.assertNotHardwareThread();   // fails fast if you broke the rule
        onSafeLoop();
    }

    @Override public final void stop() { orchestrator.close(); }

    protected abstract void onSafeInit();
    protected void onSafeLoop() {}
}
```

## Threading model

The orchestrator runs four distinct workers, each chosen for a specific role:

| Pool              | Threads         | Bounded? | Used for                                      |
| ----------------- | --------------- | -------- | --------------------------------------------- |
| **Scheduler**     | 8               | —        | `@RunPeriodically` loops                      |
| **Callback**      | 4–16            | 256      | `@SubscribedTo` handlers (with backpressure)  |
| **Action**        | unbounded       | no       | `@RunnableAction` invocations                 |
| **Hardware**      | 1 (dedicated)   | —        | All hardware reads/writes                     |

If a callback ever blocks (say, a slow `DcMotorEx` write), it cannot starve a
periodic loop — they run on separate pools. The callback pool uses
`CallerRunsPolicy` for backpressure: when the queue fills, the publisher
slows down instead of dropping messages. The action pool is unbounded so
that a long action can't be rejected under load.

## Installation

Add this to `build.dependencies.gradle` in your FTC project:

```gradle
repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://maven.pkg.github.com/IamCoder18/synapse")
        credentials {
            username = findProperty("githubUser") ?: System.getenv("GITHUB_USER") ?: System.getenv("GITHUB_ACTOR")
            password = findProperty("githubToken") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'com.aaravlabs:synapse:0.3.1'
    // ... your other FTC deps
}
```

GitHub Packages requires authentication on every download even for public
packages. Configure credentials via `~/.gradle/gradle.properties`:

```properties
githubUser=<your-github-username>
githubToken=ghp_your_token_here
```

The token only needs `read:packages` scope.

### Maven Central

`com.aaravlabs:synapse` is also published to Maven Central, so no extra
configuration is needed:

```gradle
repositories {
    mavenCentral()
}
dependencies {
    implementation 'com.aaravlabs:synapse:0.3.1'
}
```

Releases are cut by pushing a `v*` tag; GitHub Actions signs the artifacts
with GPG and pushes them through Sonatype OSSRH (`s01.oss.sonatype.org`)
where they sync to Maven Central within ~30 minutes.

## API reference

| Type | Where | Purpose |
| --- | --- | --- |
| `Orchestrator` | `com.aaravlabs.synapse.Orchestrator` | The bus. One per robot. |
| `Node` | `com.aaravlabs.synapse.Node` | An independent unit of code with annotated callbacks. |
| `Topic<T>` | `com.aaravlabs.synapse.Topic` | A named, typed channel. |
| `Subscription` | `com.aaravlabs.synapse.Subscription` | Handle returned by `subscribe()`. |
| `LogSink` | `com.aaravlabs.synapse.LogSink` | Pluggable logging output. |
| `@SubscribedTo` | `com.aaravlabs.synapse.annotation` | Callback on every published value. |
| `@RunPeriodically` | `com.aaravlabs.synapse.annotation` | Loop at a fixed frequency. |
| `@RunnableAction` | `com.aaravlabs.synapse.annotation` | One-shot, named, fire-on-demand. |
| `@OnHardwareThread` | `com.aaravlabs.synapse.annotation` | Forces a `@SubscribedTo` onto the hardware thread. |
| `SafeOpMode` | `com.aaravlabs.synapse.ftc` | Drop-in `OpMode` base class. |
| `FtcOrchestrator` | `com.aaravlabs.synapse.ftc` | Factory that wires `android.util.Log` into a `LogSink`. |
| `HardwareActions` | `com.aaravlabs.synapse.ftc` | `run` / `call` / `callAsync` / `bulkRead`. |
| `SafeDevice<T>` | `com.aaravlabs.synapse.ftc` | Hardware wrapper that routes through the hardware thread. |
| `SafeHardwareMap` | `com.aaravlabs.synapse.ftc` | `HardwareMap` that produces `SafeDevice<T>`s. |
| `GamepadAdaptor` | `com.aaravlabs.synapse.ftc` | Reflects `Gamepad` fields into topics. |
| `BulkReader` | `com.aaravlabs.synapse.ftc` | Functional interface for `bulkRead`. |

## Testing & development

The library ships with 50+ JUnit 5 tests covering topics, subscriptions,
periodic loops, two-pool isolation, hardware-thread serial execution, soak
tests, race conditions, and the real-FTC-SDK `Gamepad` field set.

```bash
./gradlew test                       # run the test suite
./gradlew publishToMavenLocal        # install into ~/.m2 for experimentation
```

Requirements: JDK 11+, Gradle 9.x.

A real working OpMode that exercises every feature lives in the
[`PubsubSmokeTest`](https://github.com/ATAARobotics/23684-Canopy-Biobuzz/blob/test/aaravlabs-pubsub/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/PubsubSmokeTest.java)
in the biobuzz test worktree.

## Contributing

Issues and PRs welcome. See [CONTRIBUTING.md](./CONTRIBUTING.md) for the
workflow, [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) for the code of
conduct, and [CHANGELOG.md](./CHANGELOG.md) for the version history.

## License

[MIT](./LICENSE)

## Credits

Created and maintained by [IamCoder18](https://github.com/IamCoder18).
