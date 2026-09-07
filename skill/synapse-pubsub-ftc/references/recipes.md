# Synapse recipes

Condensed copy-paste patterns for the most common FTC problems.

## Mecanum drive

```java
@TeleOp(name = "Mecanum")
public class Mecanum extends SafeOpMode {
    @Override protected void onSafeInit() {
        SafeDevice<DcMotorEx> fl = safeMap.device(DcMotorEx.class, "frontLeft");
        SafeDevice<DcMotorEx> fr = safeMap.device(DcMotorEx.class, "frontRight");
        SafeDevice<DcMotorEx> bl = safeMap.device(DcMotorEx.class, "backLeft");
        SafeDevice<DcMotorEx> br = safeMap.device(DcMotorEx.class, "backRight");
        orchestrator.registerNode("drive", new DriveNode(orch, fl, fr, bl, br));
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
            double y = -orchestrator.getLatestValue("g1/left_stick_y", Double.class).orElse(0.0);
            double x =  orchestrator.getLatestValue("g1/left_stick_x", Double.class).orElse(0.0);
            double r =  orchestrator.getLatestValue("g1/right_stick_x", Double.class).orElse(0.0);
            fl.run(m -> m.setPower(y + x + r));
            fr.run(m -> m.setPower(y - x - r));
            bl.run(m -> m.setPower(y - x + r));
            br.run(m -> m.setPower(y + x - r));
        }
    }
}
```

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
