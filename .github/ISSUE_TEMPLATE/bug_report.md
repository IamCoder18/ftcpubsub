---
name: Bug report
about: Something doesn't work
title: ''
labels: bug
assignees: ''
---

## What happened

A clear, concise description of the bug.

## How to reproduce

A minimal OpMode that triggers the issue. Pseudocode is fine; a pasteable code
block is better.

```java
public class Repro extends SafeOpMode {
    @Override protected void onSafeInit() { ... }
    @Override protected void onSafeLoop()  { ... }
}
```

## Versions

- AaravLabs pub/sub: `com.aaravlabs:pubsub:X.Y.Z`
- FTC SDK (RobotCore version): `org.firstinspires.ftc:RobotCore:X.Y.Z`
- Android Gradle Plugin:
- Gradle:

## Expected vs actual

**Expected:** ...
**Actual:** ...

## Stacktrace (if applicable)

```
paste here
```
