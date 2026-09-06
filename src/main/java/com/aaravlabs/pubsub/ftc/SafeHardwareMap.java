package com.aaravlabs.pubsub.ftc;

import com.qualcomm.robotcore.hardware.HardwareMap;

/**
 * Wraps the FTC SDK's {@link HardwareMap} so every device lookup returns a
 * {@link SafeDevice} whose method calls route through the hardware thread.
 *
 * <p>Usage:
 * <pre>{@code
 * SafeHardwareMap safe = new SafeHardwareMap(hardwareMap, orch.hardware());
 * SafeDevice<DcMotorEx> intake = safe.device(DcMotorEx.class, "intake");
 * intake.run(m -> m.setPower(0.5));
 * }</pre>
 *
 * <p>The raw {@code get(Class, String)} method is invoked directly here
 * (compile-time SDK dependency via the decompiled stub). At runtime the user's
 * project provides the real SDK implementation.
 */
public final class SafeHardwareMap {

    private final HardwareMap rawMap;
    private final HardwareActions hardware;

    public SafeHardwareMap(HardwareMap rawMap, HardwareActions hardware) {
        this.rawMap = rawMap;
        this.hardware = hardware;
    }

    /**
     * Look up a hardware device by class and name; return a {@link SafeDevice}
     * wrapper.
     */
    public <T> SafeDevice<T> device(Class<T> deviceClass, String name) {
        T raw = rawMap.get(deviceClass, name);
        return new SafeDevice<>(raw, hardware);
    }

    /** @return the raw underlying {@link HardwareMap}. */
    public HardwareMap raw() {
        return rawMap;
    }
}
