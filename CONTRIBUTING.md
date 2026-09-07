# Contributing

Thanks for your interest in contributing to Synapse! This document
covers how to set up the project locally, run the test suite, and submit changes.

## Code of conduct

By participating, you agree to keep things respectful and constructive. We're
all here to make FTC software better.

## Project layout

This is a single-module Gradle project:

- `src/main/java/com/aaravlabs/synapse/` — the library.
- `src/main/java/com/aaravlabs/synapse/ftc/` — FTC-specific helpers
  (`SafeOpMode`, `HardwareActions`, `GamepadAdaptor`, ...).
- `src/main/java/com/aaravlabs/synapse/annotation/` — annotation types.
- `src/main/java/com/aaravlabs/synapse/internal/` — reflection-based binder;
  not part of the public API.
- `src/main/resources/META-INF/proguard/synapse.pro` — R8 keep rules.
- `libs/ftc-sdk-stub.jar` — `compileOnly` stub of `com.qualcomm.robotcore.*`
  classes (built from `FtcRobotController-release.apk` v11.2). Consumers have
  the real SDK at runtime, so this stub is **never published**.
- `src/test/java/com/aaravlabs/synapse/` — JUnit 5 tests.

## Building & testing

Requirements: JDK 11+, a single Gradle 9.x install (the wrapper is not
checked in).

```bash
# Run the test suite.
./gradlew test

# Publish to your local maven repo for experimentation.
./gradlew publishToMavenLocal

# Publish to GitHub Packages (requires `gpr.user` + `gpr.key` or `GITHUB_TOKEN`).
GITHUB_USER=<your-github-username> GITHUB_TOKEN=$(gh auth token) \
    ./gradlew publishMavenPublicationToGitHubPackagesRepository
```

## Coding conventions

- **Minimal comments.** Code should speak for itself; prefer obvious naming and
  short methods over commented code.
- **No new dependencies** unless absolutely necessary. The library has zero
  runtime dependencies today; please keep it that way.
- **Tests for new code.** Every annotation or thread-pool routing change needs
  a JUnit 5 test demonstrating the safety property.
- **R8 survival.** All reflection paths must keep working after `r8` minification.
  Add proguard rules to `src/main/resources/META-INF/proguard/synapse.pro` if you
  introduce new reflection targets.

## Releasing

Releases are tagged versions. To cut a release:

1. Update `version` in `build.gradle`.
2. Add a heading to `CHANGELOG.md` describing what changed.
3. Commit on `main`.
4. Tag: `git tag vX.Y.Z`.
5. Push: `git push origin main --tags`.
6. Publish: `GITHUB_USER=... GITHUB_TOKEN=... ./gradlew publishMavenPublicationToGitHubPackagesRepository`.

## Reporting issues

Please include:
- A minimal OpMode that reproduces the problem.
- The Synapse library version (`com.aaravlabs:synapse:X.Y.Z`).
- The FTC SDK version (`RobotCore:X.Y.Z`).
- What you expected vs. what happened.

Issues go at <https://github.com/IamCoder18/synapse/issues>.
