package com.aaravlabs.synapse.ftc;

import com.qualcomm.robotcore.hardware.HardwareMap;

/**
 * Wraps the FTC SDK's {@link HardwareMap} so every device lookup returns a
 * {@link SafeDevice} whose method calls route through the hardware thread.
 *
 * <p>Usage:
 * <pre>{@code
 * SafeHardwareMap safe = new SafeHardwareMap(hardwareMap, orchestrator.hardware());
 * SafeDevice<DcMotorEx> intake = safe.device(DcMotorEx.class, "intake");
 * intake.run(m -> m.setPower(0.5));
 * }</pre>
 *
 * <p>{@code SafeOpMode} creates one for you as {@code safeMap}; construct your own
 * only outside an OpMode (e.g. in tests).
 */
public final class SafeHardwareMap {

    private final HardwareMap rawMap;
    private final HardwareActions hardware;

    /**
     * Create a safe map over the given SDK {@code HardwareMap}.
     *
     * @param rawMap the SDK {@code HardwareMap} to wrap
     * @param hardware the hardware-thread facade to route calls through
     */
    public SafeHardwareMap(HardwareMap rawMap, HardwareActions hardware) {
        this.rawMap = rawMap;
        this.hardware = hardware;
    }

    /**
     * Look up a hardware device by class and name; return a {@link SafeDevice}
     * wrapper.
     *
     * @param deviceClass the SDK device class (e.g. {@code DcMotorEx.class})
     * @param name the device name from the robot configuration
     * @param <T> the device type
     * @return a wrapper that routes all calls through the hardware thread
     */
    public <T> SafeDevice<T> device(Class<T> deviceClass, String name) {
        T raw = rawMap.get(deviceClass, name);
        return new SafeDevice<>(raw, hardware);
    }

    /**
     * @return the raw underlying {@link HardwareMap}
     */
    public HardwareMap raw() {
        return rawMap;
    }
}
