package com.aaravlabs.pubsub.ftc;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A generic wrapper around any FTC hardware object that routes every method
 * call through the orchestrator's hardware thread.
 *
 * <p>Use {@link SafeDevice#run(Consumer)} for fire-and-forget operations
 * (e.g. {@code device.run(m -> m.setPower(0.5))}) and {@link SafeDevice#call(Function)}
 * for reads that return a value (e.g. {@code device.call(DcMotorEx::getCurrentPosition)}).
 *
 * <p>Both methods submit the work to the single hardware thread and execute it
 * serially with respect to every other piece of hardware-thread work in the
 * orchestrator. This eliminates race conditions on the Lynx bus and other
 * non-thread-safe hardware APIs.
 *
 * <p>For advanced cases where you must call many methods atomically, use
 * {@link SafeDevice#raw()} to get the unwrapped object — but you must only
 * touch it from the hardware thread (e.g. inside a {@link HardwareActions#run}
 * callback or a {@code @OnHardwareThread} subscriber).
 *
 * <p>Example:
 * <pre>{@code
 * SafeDevice<DcMotorEx> intake = hardware.device(DcMotorEx.class, "intake");
 * intake.run(m -> m.setPower(0.5));                     // async, safe
 * int pos = intake.call(DcMotorEx::getCurrentPosition); // sync, blocks caller
 * }</pre>
 */
public final class SafeDevice<T> {

    private final T raw;
    private final HardwareActions hardware;

    public SafeDevice(T raw, HardwareActions hardware) {
        this.raw = raw;
        this.hardware = hardware;
    }

    /**
     * Schedule {@code action} to run on the hardware thread with this device as
     * its argument. Returns immediately.
     */
    public void run(Consumer<? super T> action) {
        hardware.run(() -> action.accept(raw));
    }

    /**
     * Run {@code action} on the hardware thread, blocking the caller until it
     * completes. Returns the value produced by {@code action}.
     */
    public <R> R call(Function<? super T, ? extends R> action) throws Exception {
        return hardware.call(() -> action.apply(raw));
    }

    /**
     * Apply {@code action} to the device on the hardware thread; return a
     * {@link java.util.concurrent.CompletableFuture} for the result. Non-blocking.
     */
    public <R> java.util.concurrent.CompletableFuture<R> callAsync(
            Function<? super T, ? extends R> action) {
        return hardware.callAsync(() -> action.apply(raw));
    }

    /**
     * Get the raw, unwrapped hardware object. <b>Use with caution</b>: any method
     * you call on the returned object runs on the caller's thread, not the hardware
     * thread. Only use this from inside a {@link HardwareActions#run} callback,
     * a {@code @OnHardwareThread} subscriber, or another piece of code already
     * executing on the hardware thread.
     */
    public T raw() {
        return raw;
    }

    @Override
    public String toString() {
        return "SafeDevice{" + raw.getClass().getSimpleName() + "}";
    }
}
