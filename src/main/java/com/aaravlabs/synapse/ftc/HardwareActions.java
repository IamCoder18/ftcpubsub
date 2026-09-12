package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Orchestrator;
import com.aaravlabs.synapse.OrchestratorImpl;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A typed facade over the orchestrator's hardware thread. Provides four ways to
 * interact with FTC hardware safely:
 *
 * <ul>
 *   <li>{@link #run(Runnable)} — async, returns immediately.</li>
 *   <li>{@link #call(Callable)} — synchronous, blocks caller until the hardware
 *       thread completes the work and returns the value.</li>
 *   <li>{@link #callAsync(Callable)} — non-blocking, returns a
 *       {@link CompletableFuture}.</li>
 *   <li>{@link #bulkRead(int, BulkReader)} — periodic, runs the callback on the
 *       hardware thread at a fixed rate and lets it publish values to topics.</li>
 * </ul>
 *
 * <p>Every method on this class routes to the orchestrator's dedicated single
 * hardware thread. Code that touches {@code HardwareMap} devices should always
 * go through this facade.
 *
 * <p>Obtain an instance via {@code orchestrator.hardware()}.
 */
public final class HardwareActions {

    private final OrchestratorImpl orchestrator;

    public HardwareActions(OrchestratorImpl orchestrator) {
        this.orchestrator = orchestrator;
    }

    // ------------------------------------------------------------------
    // async — fire-and-forget
    // ------------------------------------------------------------------

    /**
     * Schedule {@code action} to run on the hardware thread. Returns immediately;
     * the action runs in the future on the dedicated hardware thread.
     */
    public void run(Runnable action) {
        orchestrator.runOnHardwareThread(action);
    }

    // ------------------------------------------------------------------
    // sync — block caller until the hardware thread completes the work
    // ------------------------------------------------------------------

    /**
     * Run {@code action} on the hardware thread, blocking the caller until it
     * completes. Returns the value the callable produced.
     *
     * <p>Useful when you need to read a hardware value from the OpMode loop
     * without blocking the hardware thread from doing other work — the caller
     * waits, but the hardware thread can still service other queued work in
     * parallel (sequentially, on its single thread).
     *
     * @throws Exception whatever the callable threw
     */
    public <T> T call(Callable<T> action) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        orchestrator.runOnHardwareThread(() -> {
            try {
                f.complete(action.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            // No timeout by default — the hardware thread is single-threaded
            // and any in-flight work will eventually drain. Callers who need a
            // timeout can wrap with .get(timeout, unit) themselves.
            return f.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (ExecutionException ee) {
            // Unwrap so callers see the original exception.
            Throwable cause = ee.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw ee;
        }
    }

    /**
     * Same as {@link #call(Callable)} but with an explicit timeout.
     */
    public <T> T call(Callable<T> action, long timeout, TimeUnit unit) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        orchestrator.runOnHardwareThread(() -> {
            try { f.complete(action.call()); }
            catch (Throwable t) { f.completeExceptionally(t); }
        });
        try {
            return f.get(timeout, unit);
        } catch (TimeoutException te) {
            f.cancel(true);
            throw te;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw ee;
        }
    }

    // ------------------------------------------------------------------
    // async with future — fire-and-forget that returns a value
    // ------------------------------------------------------------------

    /**
     * Schedule {@code action} on the hardware thread; return a future that
     * completes with the action's result. Non-blocking.
     */
    public <T> CompletableFuture<T> callAsync(Callable<T> action) {
        CompletableFuture<T> f = new CompletableFuture<>();
        orchestrator.runOnHardwareThread(() -> {
            try {
                f.complete(action.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    // ------------------------------------------------------------------
    // bulk read — periodic hardware reads that publish to topics
    // ------------------------------------------------------------------

    /**
     * Schedule {@code reader} to be invoked on the hardware thread at
     * {@code hz} hertz. The reader receives a {@link HardwareView} it can
     * publish values through. Useful for sensor polling patterns:
     *
     * <pre>{@code
     * hw.bulkRead(50, view -> {
     *     double amps = motor.getCurrent(CurrentUnit.AMPS);
     *     view.publish("motor/amps", amps);
     * });
     * }</pre>
     *
     * <p>The reader runs on the hardware thread and shares it with all other
     * hardware work, so it cannot overlap with other hardware-thread callbacks
     * or {@code @RunPeriodically(hardware = true)} loops.
     *
     * <p>Returns a handle that can be passed to {@link #stopBulkRead} to cancel.
     */
    public BulkReadHandle bulkRead(int hz, BulkReader reader) {
        return orchestrator.scheduleHardwareBulkRead(hz, reader);
    }

    /**
     * Cancel a previously-registered bulk-read. Safe to call on an already-stopped
     * handle.
     */
    public void stopBulkRead(BulkReadHandle handle) {
        if (handle != null) handle.cancel();
    }

    // ------------------------------------------------------------------
    // diagnostics — used by SafeOpMode to assert thread identity
    // ------------------------------------------------------------------

    /**
     * @return true if the current thread is the orchestrator's hardware thread.
     *         Used internally by {@code SafeOpMode} and {@code SafeDevice} to
     *         enforce thread safety.
     */
    public boolean isHardwareThread() {
        return orchestrator.isHardwareThread();
    }

    /**
     * Throw an {@link IllegalStateException} if the current thread is the
     * hardware thread. Useful in {@code OpMode.loop()} to fail-fast if the user
     * accidentally scheduled the loop on the hardware thread.
     */
    public void assertNotHardwareThread() {
        if (isHardwareThread()) {
            throw new IllegalStateException(
                    "This code is running on the hardware thread. OpMode.loop() " +
                    "and similar lifecycle hooks must run on the OpMode thread, " +
                    "publishing to topics. Use HardwareActions.run() to schedule " +
                    "hardware-thread work.");
        }
    }

    /**
     * Called once per OpMode loop iteration. Processes any pending hardware-thread
     * tasks. {@code SafeOpMode.loop()} calls this for you; if you don't use
     * {@code SafeOpMode}, call it yourself.
     */
    public void tick() {
        // Currently a no-op: the hardware thread processes its own queue
        // independently. Reserved for future hook (e.g. flushing telemetry).
    }

    /**
     * Shut down the underlying hardware thread. Called by {@code SafeOpMode.stop()}.
     * After this, all hardware methods will be ignored.
     */
    public void shutdown() {
        orchestrator.close();
    }

    /** Handle returned from {@link #bulkRead}, used to cancel. */
    public static final class BulkReadHandle {
        private final java.util.concurrent.ScheduledFuture<?> future;
        public BulkReadHandle(java.util.concurrent.ScheduledFuture<?> f) { this.future = f; }
        public void cancel() { future.cancel(false); }
    }
}
