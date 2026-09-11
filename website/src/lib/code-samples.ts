export const QUICKSTART_CODE = `@TeleOp(name = "SynapseDemo", group = "Demo")
public class SynapseDemo extends SafeOpMode {

  @Override
  protected void onSafeInit() {
    SafeDevice<DcMotorEx> left =
        safeMap.device(DcMotorEx.class, "leftMotor");
    SafeDevice<DcMotorEx> right =
        safeMap.device(DcMotorEx.class, "rightMotor");
    orchestrator.registerNode("drive",
        new DriveNode(orchestrator, left, right));
    GamepadAdaptor.attach(orchestrator, gamepad1, "g1");
  }
}`

export const PUBSUB_CODE = `public static class IntakeNode extends Node {

  @SubscribedTo(topic = "g1/right_bumper/rising")
  @OnHardwareThread
  public void onPress(Boolean v) {
    intake.run(m -> m.setPower(1.0));
  }

  @RunPeriodically(hz = 10)
  public void publishTelemetry() {
    orchestrator.publish("intake/power", lastPower);
  }
}`

export const HARDWARE_CODE = `HardwareActions hw = orchestrator.hardware();

hw.run(() -> motor.setPower(0.5));
double pos = hw.call(() -> motor.getCurrentPosition());

hw.bulkRead(50, view -> {
  double amps = motor.getCurrent(CurrentUnit.AMPS);
  view.publish("motor/amps", amps);
});`
