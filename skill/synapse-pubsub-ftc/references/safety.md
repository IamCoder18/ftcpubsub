# Synapse safety invariants

The four properties that make Synapse safe to use in production. Each cites the source and the test that proves it.

## 1. Single hardware thread

**Claim.** Every piece of code that touches FTC hardware runs on the same single OS thread, executing strictly serially.

**Where it's enforced.** `src/main/java/com/aaravlabs/synapse/OrchestratorImpl.java:93-94` — `Executors.newSingleThreadScheduledExecutor(hwTf)`.

**Test.** `src/test/java/com/aaravlabs/synapse/HardwareThreadTest.java` — six `@Test` methods covering serial execution, ordering under load, and the `assertNotHardwareThread()` path.

## 2. Two-pool isolation

**Claim.** `@RunPeriodically` loops and `@SubscribedTo` callbacks run on separate thread pools, so a slow subscriber cannot starve a periodic loop and vice-versa.

**Where it's enforced.** `OrchestratorImpl.java:76` (scheduler, size 8) and `OrchestratorImpl.java:78-82` (callbacks, 4–16 threads, bounded queue of 256 with `CallerRunsPolicy`).

**Test.** `src/test/java/com/aaravlabs/synapse/ThreadingTest.java:16` and `:50`.

## 3. Copy-on-write subscriber snapshots

**Claim.** Subscribers are stored in a copy-on-write list under a lock, so publishers iterate a stable snapshot and subscribers can join/leave mid-dispatch without throwing `ConcurrentModificationException`.

**Where it's enforced.** `OrchestratorImpl.java:538-576` (`SubscriberList`).

**Test.** `src/test/java/com/aaravlabs/synapse/SoakTest.java:32`, `:85`, `:121` — long-running soak tests with concurrent publishers and subscribers.

## 4. Fail-fast thread assertion

**Claim.** If the OpMode `loop()` ever runs on the hardware thread, Synapse throws `IllegalStateException` immediately rather than silently corrupting the bus.

**Where it's enforced.** `src/main/java/com/aaravlabs/synapse/ftc/SafeOpMode.java:62-66` (`hardware.assertNotHardwareThread()` at the top of `loop()`).

**Test.** `src/test/java/com/aaravlabs/synapse/ftc/HardwareActionsTest.java` — exercises the `isHardwareThread` and `assertNotHardwareThread` paths.
