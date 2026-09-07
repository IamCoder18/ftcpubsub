package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Orchestrator;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;

/**
 * Base class for {@link OpMode} subclasses that use the Synapse pub/sub bus
 * with hardware-thread safety. Extends the FTC SDK's {@code OpMode} and
 * provides:
 *
 * <ul>
 *   <li>Orchestrator lifecycle: created on {@code init()}, shut down on
 *       {@code stop()}.</li>
 *   <li>A {@link HardwareActions} instance ready to use as {@code hardware}.</li>
 *   <li>A {@link SafeHardwareMap} that wraps {@code hardwareMap} so device
 *       method calls route through the hardware thread.</li>
 *   <li>A {@code loop()} body that asserts you are not running on the
 *       hardware thread (fail-fast if a bug sneaks in) and ticks the
 *       {@link HardwareActions}.</li>
 * </ul>
 *
 * <p>To use, extend this class instead of {@code OpMode}:
 *
 * <pre>{@code
 * @TeleOp(name = "My OpMode")
 * public class MyOpMode extends SafeOpMode {
 *     @Override protected void onSafeInit() {
 *         // hardwareMap, hardware, orch, safeMap are all initialized for you.
 *         SafeDevice<DcMotorEx> intake = safeMap.device(DcMotorEx.class, "intake");
 *         orch.registerNode("intake", new IntakeNode(orch, intake));
 *     }
 *
 *     @Override protected void onSafeLoop() {
 *         // Default loop body. You may also override loop() yourself if you
 *         // need full control, but then call super.loop() to get the
 *         // thread assertion + tick.
 *         orch.publish("tick", System.nanoTime());
 *     }
 * }
 * }</pre>
 */
public abstract class SafeOpMode extends OpMode {

    protected Orchestrator orch;
    protected HardwareActions hardware;
    protected SafeHardwareMap safeMap;

    @Override
    public final void init() {
        orch = FtcOrchestrator.create();
        hardware = orch.hardware();
        safeMap = new SafeHardwareMap(hardwareMap, hardware);
        onSafeInit();
    }

    @Override
    public void init_loop() {
        onSafeInitLoop();
    }

    @Override
    public void loop() {
        hardware.assertNotHardwareThread();
        hardware.tick();
        onSafeLoop();
    }

    @Override
    public void start() {
        onSafeStart();
    }

    @Override
    public void stop() {
        onSafeStop();
        orch.close();
    }

    // ---- user-overridable hooks ----------------------------------------

    /** Required: build your nodes and register them here. */
    protected abstract void onSafeInit();

    /** Called every iteration after start(); defaults to no-op. */
    protected void onSafeLoop() {}

    /** Called repeatedly between init and start; defaults to no-op. */
    protected void onSafeInitLoop() {}

    /** Called when the OpMode starts. Defaults to no-op. */
    protected void onSafeStart() {}

    /** Called when the OpMode stops, before orch is closed. Defaults to no-op. */
    protected void onSafeStop() {}
}
