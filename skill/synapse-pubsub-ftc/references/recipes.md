# Synapse recipes

Condensed copy-paste patterns for the most common FTC problems. Variables come from a `SafeOpMode` subclass (e.g. `orch`, `hardware`, `safeMap`, `gamepad1`), so use those names when copying snippets into your own code.

## Mecanum drive (robot-centric)

```java
@TeleOp(name = "Mecanum")
public class Mecanum extends SafeOpMode {
    @Override protected void onSafeInit() {
        SafeDevice<DcMotorEx> fl = safeMap.device(DcMotorEx.class, "frontLeft");
        SafeDevice<DcMotorEx> fr = safeMap.device(DcMotorEx.class, "frontRight");
        SafeDevice<DcMotorEx> bl = safeMap.device(DcMotorEx.class, "backLeft");
        SafeDevice<DcMotorEx> br = safeMap.device(DcMotorEx.class, "backRight");
        orchestrator.registerNode("drive", new DriveNode(orchestrator, fl, fr, bl, br));
        GamepadAdaptor.attach(orchestrator, gamepad1, "g1");
    }

    public static class DriveNode extends Node {
        DriveNode(Orchestrator orch, SafeDevice<DcMotorEx> fl, SafeDevice<DcMotorEx> fr,
                  SafeDevice<DcMotorEx> bl, SafeDevice<DcMotorEx> br) {
            super(orch); this.fl = fl; this.fr = fr; this.bl = bl; this.br = br;
        }
        private final SafeDevice<DcMotorEx> fl, fr, bl, br;

        @RunPeriodically(hz = 50, hardware = true)
        public void drive() {
            double y = -orchestrator.getLatestValue("g1/left_stick_y", Float.class).map(Float::doubleValue).orElse(0.0);
            double x =  orchestrator.getLatestValue("g1/left_stick_x", Float.class).map(Float::doubleValue).orElse(0.0);
            double r =  orchestrator.getLatestValue("g1/right_stick_x", Float.class).map(Float::doubleValue).orElse(0.0);

            // Compute all four raw powers, then normalize so |power| ≤ 1.
            double pFL = y + x + r;
            double pFR = y - x - r;
            double pBL = y - x + r;
            double pBR = y + x - r;
            double max = Math.max(1.0, Math.max(Math.abs(pFL),
                              Math.max(Math.abs(pFR),
                              Math.max(Math.abs(pBL), Math.abs(pBR)))));
            double nFL = pFL / max;
            double nFR = pFR / max;
            double nBL = pBL / max;
            double nBR = pBR / max;

            fl.run(m -> m.setPower(nFL));
            fr.run(m -> m.setPower(nFR));
            bl.run(m -> m.setPower(nBL));
            br.run(m -> m.setPower(nBR));
        }
    }
}
```

This is **robot-centric** mecanum (joystick inputs go straight to the motor mix). For field-centric control, add a heading-based rotation of `(x, y)` before the mix. `DcMotorEx.setPower` requires values in `[-1, 1]`, so we normalize to avoid clipping when the stick vector is large.

## Bulk read sensors

```java
hardware.bulkRead(50, view -> {
    SafeDevice<DcMotorEx> intake = safeMap.device(DcMotorEx.class, "intake");
    intake.call(m -> {
        view.publish("intake/amps", m.getCurrent(CurrentUnit.AMPS));
        view.publish("intake/pos",  m.getCurrentPosition());
        return null;
    });
});
```

## Two-controller teleop

```java
@Override
protected void onSafeInit() {
    GamepadAdaptor.attach(orchestrator, gamepad1, "driver");
    GamepadAdaptor.attach(orchestrator, gamepad2, "operator");
}
```

Then subscribe to `driver/...` and `operator/...` topics.

## Button-triggered action

```java
@SubscribedTo(topic = "driver/x/rising")
public void onXPress(Boolean v) {
    orchestrator.runAction("fire");
}

@RunnableAction("fire")
public void fire() { /* … */ }
```
