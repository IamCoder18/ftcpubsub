# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.1] - 2026-09-06

### Changed

- Repackaged with the FTC SDK reference classes moved to `compileOnly` stubs at
  `libs/ftc-sdk-stub.jar` so the published JAR no longer conflicts with the real
  RobotCore at the user's compile time.

## [0.2.0] - 2026-09-06

### Added

- `@SubscribedTo`, `@RunPeriodically`, `@RunnableAction`, and `@OnHardwareThread`
  annotations.
- `SafeOpMode` base class extending `com.qualcomm.robotcore.eventloop.opmode.OpMode`,
  with built-in thread assertion and orchestrator lifecycle.
- `HardwareActions` facade with `run`, `call`, `callAsync`, and `bulkRead`
  methods that route work to the dedicated hardware thread.
- `SafeDevice<T>` generic wrapper around any hardware object.
- `SafeHardwareMap` thin wrapper around the FTC SDK's `HardwareMap`.
- `GamepadAdaptor` reflection-based gamepad-to-topic publisher.
- Two-pool executor model: separate scheduled and callback thread pools with
  blocking-queue backpressure; an unbounded action pool for `@RunnableAction`.
- Dedicated single-threaded hardware executor for all hardware-touching code.
- Cross-thread type normalization (`double` ↔ `Double`) so annotation-bound
  primitive parameters and programmatically-typed topics interoperate.
- `isAssignableFrom`-based publish type checks (so an `Object`-typed topic
  accepts any value).
- Auto-creation of topics on first publish (a-la Heron's behavior).
- JUnit 5 test suite of ~53 tests covering topics, subscriptions, periodic
  loops, two-pool isolation, hardware-thread serial execution, soak tests,
  race conditions, and the real-FTC-SDK `Gamepad` field set.

[Unreleased]: https://github.com/IamCoder18/ftcpubsub/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/IamCoder18/ftcpubsub/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/IamCoder18/ftcpubsub/releases/tag/v0.2.0
