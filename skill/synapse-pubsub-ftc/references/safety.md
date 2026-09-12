# Synapse safety invariants

The four properties Synapse provides around FTC hardware access. Each cites the source and the test that exercises it. Important: every claim here is scoped to **Synapse-routed** operations. Code that touches hardware directly without going through the orchestrator is outside these invariants.

## 1. Single-threaded execution of Synapse-routed hardware work

**Claim.** Every piece of code that touches FTC hardware *and is routed through Synapse* (via `@OnHardwareThread`, `@RunPeriodically(hardware = true)`, `HardwareActions.run`, `HardwareActions.call`, `HardwareActions.callAsync`, `HardwareActions.bulkRead`, or `SafeDevice.run`) runs on the same single OS thread and executes strictly serially.

**Where it's enforced.** `src/main/java/com/aaravlabs/synapse/OrchestratorImpl.java:93-94` — `Executors.newSingleThreadScheduledExecutor(hwTf)`.

**Out of scope.** Ad-hoc hardware calls from code that is *not* routed through Synapse (e.g. raw `motor.setPower(...)` from `SafeOpMode.loop()` or from a non-annotated callback) bypass this thread entirely. Such code is outside these invariants; the `HardwareActions.assertNotHardwareThread()` check in `SafeOpMode.loop()` only proves that the *OpMode loop itself* is not running on the hardware thread. It does not detect or intercept raw ad-hoc writes.

**Test.** `src/test/java/com/aaravlabs/synapse/HardwareThreadTest.java` — six `@Test` methods covering serial execution, ordering under load, and the `assertNotHardwareThread()` path.

## 2. Two-pool isolation under normal load

**Claim.** `@RunPeriodically` loops run on the scheduler pool; `@SubscribedTo` callbacks run on the callback pool. Under normal load a slow subscriber cannot starve a periodic loop and vice-versa.

**Where it's enforced.** `OrchestratorImpl.java:76` (scheduler, size 8) and `OrchestratorImpl.java:78-82` (callbacks, 4–16 threads, bounded queue of 256 with `CallerRunsPolicy`).

**Caveat.** The callback pool's `CallerRunsPolicy` runs the submitted task on the publisher's thread if the queue is full. A scheduler task that calls `publish` can therefore end up running a slow callback on its own thread, briefly reducing isolation under saturation. Treat the two-pool isolation as a steady-state property, not a hard guarantee under backpressure.

**Test.** `src/test/java/com/aaravlabs/synapse/ThreadingTest.java:16` and `:50`.

## 3. Copy-on-write subscriber snapshots

**Claim.** Subscribers are stored in a copy-on-write list under a lock, so publishers iterate a stable snapshot and subscribers can join/leave mid-dispatch without throwing `ConcurrentModificationException`.

**Where it's enforced.** `OrchestratorImpl.java:538-576` (`SubscriberList`).

**Test.** `src/test/java/com/aaravlabs/synapse/SoakTest.java:32`, `:85`, `:121` — long-running soak tests with concurrent publishers and subscribers.

## 4. Fail-fast thread assertion in `SafeOpMode`

**Claim.** If `SafeOpMode.loop()` ever runs on the hardware thread, Synapse throws `IllegalStateException` immediately rather than silently corrupting the bus.

**Where it's enforced.** `src/main/java/com/aaravlabs/synapse/ftc/SafeOpMode.java:71-76` (`hardware.assertNotHardwareThread()` at the top of `loop()`).

**Test.** `src/test/java/com/aaravlabs/synapse/ftc/HardwareActionsTest.java` — exercises the `isHardwareThread` and `assertNotHardwareThread` paths.
