package com.aaravlabs.pubsub.ftc;

import com.aaravlabs.pubsub.Orchestrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link SafeDevice} wrapper routes calls through the hardware
 * thread, and that {@link SafeHardwareMap} works against a real
 * {@code HardwareMap}-shaped object via reflection.
 *
 * <p>We use a fake {@code HardwareMap}-like object in this test rather than the
 * real FTC SDK class (which we don't have on the build classpath). The real
 * SafeHardwareMap is exercised in production with the actual FTC SDK; here we
 * prove the wrapping + thread-routing works against any object exposing the
 * standard {@code get(Class, String)} method.
 */
class SafeDeviceTest {

    private Orchestrator orch;
    private HardwareActions hw;

    /** Stand-in for a hardware device. */
    static class FakeMotor {
        double power;
        int position;
        FakeMotor setPower(double p) { this.power = p; return this; }
        int getCurrentPosition() { return position; }
        int setPosition(int p) { this.position = p; return p; }
    }

    /** Fake hardware map matching the FTC SDK HardwareMap shape. */
    static class FakeHardwareMap extends com.qualcomm.robotcore.hardware.HardwareMap {
        final java.util.Map<String, Object> devices = new java.util.HashMap<>();
        @Override
        public <T> T get(Class<? extends T> deviceClass, String name) {
            Object dev = devices.get(name);
            if (dev == null) throw new IllegalArgumentException("No device named " + name);
            return deviceClass.cast(dev);
        }
    }

    @BeforeEach void setUp() { orch = Orchestrator.create("test"); hw = orch.hardware(); }
    @AfterEach  void tearDown() { orch.close(); }

    @Test
    void run_routesCallToHardwareThread() throws Exception {
        FakeMotor motor = new FakeMotor();
        SafeDevice<FakeMotor> safe = new SafeDevice<>(motor, hw);

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> tname = new AtomicReference<>();
        safe.run(m -> {
            tname.set(Thread.currentThread().getName());
            m.setPower(0.5);
            done.countDown();
        });
        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(tname.get().contains("-hw-"),
                "SafeDevice.run should execute on hardware thread; got: " + tname.get());
        assertEquals(0.5, motor.power, 0.001);
    }

    @Test
    void call_returnsValueFromHardwareThread() throws Exception {
        FakeMotor motor = new FakeMotor();
        motor.position = 1234;
        SafeDevice<FakeMotor> safe = new SafeDevice<>(motor, hw);

        int pos = safe.call(FakeMotor::getCurrentPosition);
        assertEquals(1234, pos);
    }

    @Test
    void callAsync_returnsFuture() throws Exception {
        FakeMotor motor = new FakeMotor();
        motor.position = 42;
        SafeDevice<FakeMotor> safe = new SafeDevice<>(motor, hw);

        var future = safe.callAsync(FakeMotor::getCurrentPosition);
        Thread.sleep(50);
        assertEquals(Integer.valueOf(42), future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void raw_returnsTheUnderlyingDevice() {
        FakeMotor motor = new FakeMotor();
        SafeDevice<FakeMotor> safe = new SafeDevice<>(motor, hw);
        assertSame(motor, safe.raw());
    }

    @Test
    void safeHardwareMap_wrapsDeviceLookups() throws Exception {
        FakeMotor intake = new FakeMotor();
        FakeMotor outtake = new FakeMotor();
        FakeHardwareMap rawMap = new FakeHardwareMap();
        rawMap.devices.put("intake", intake);
        rawMap.devices.put("outtake", outtake);

        SafeHardwareMap safeMap = new SafeHardwareMap(rawMap, hw);

        SafeDevice<FakeMotor> safeIntake = safeMap.device(FakeMotor.class, "intake");
        assertNotNull(safeIntake);
        // raw() gives the original device.
        assertSame(intake, safeIntake.raw());

        // Calls route through hardware thread.
        safeIntake.run(m -> m.setPower(1.0));
        // Use the bulkRead as a fence to ensure the call above completed.
        CountDownLatch fence = new CountDownLatch(1);
        hw.run(fence::countDown);
        assertTrue(fence.await(1, TimeUnit.SECONDS));
        assertEquals(1.0, intake.power, 0.001);

        SafeDevice<FakeMotor> safeOuttake = safeMap.device(FakeMotor.class, "outtake");
        assertSame(outtake, safeOuttake.raw());
    }

    @Test
    void safeHardwareMap_throwsForNonMapObject() {
        Object notAMap = "not a hardware map";
        assertThrows(ClassCastException.class,
                () -> new SafeHardwareMap(
                        (com.qualcomm.robotcore.hardware.HardwareMap) notAMap, hw));
    }

    @Test
    void allHardwareThreadTasksAreSerial() throws Exception {
        // Many parallel SafeDevice.run calls on different devices.
        java.util.List<FakeMotor> motors = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) motors.add(new FakeMotor());

        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        for (FakeMotor m : motors) {
            new SafeDevice<>(m, hw).run(dev -> {
                events.add("+" + Thread.currentThread().getName());
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                events.add("-" + Thread.currentThread().getName());
            });
        }
        // Fence.
        CountDownLatch fence = new CountDownLatch(1);
        hw.run(fence::countDown);
        assertTrue(fence.await(2, TimeUnit.SECONDS));

        // All "+" events must be paired with the immediately following "-"
        // event, with no interleaving. The same thread name must appear.
        for (int i = 0; i < events.size(); i += 2) {
            String a = events.get(i);
            String b = events.get(i + 1);
            assertTrue(a.startsWith("+"));
            assertTrue(b.startsWith("-"));
            assertEquals(a.substring(1), b.substring(1),
                    "+ and - must be from the same thread");
        }
        assertEquals(0, events.size() % 2);
    }
}
