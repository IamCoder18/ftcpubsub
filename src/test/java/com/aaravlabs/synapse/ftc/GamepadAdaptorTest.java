package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Orchestrator;
import com.aaravlabs.synapse.Subscription;
import com.qualcomm.robotcore.hardware.Gamepad;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link GamepadAdaptor} correctly enumerates the public boolean and
 * float fields of the <b>real</b> FTC SDK {@link Gamepad} class shape. The test
 * {@code Gamepad} class in this module is extracted from
 * {@code FtcRobotController-release.apk} v11.2 via baksmali/jadx and has the exact
 * field names, types, and modifiers the SDK ships.
 *
 * <p>This proves the reflection-based discovery path used by {@code GamepadAdaptor}
 * handles every field the SDK exposes — present and any future additions.
 */
class GamepadAdaptorTest {

    private Orchestrator orch;

    @BeforeEach void setUp() { orch = FtcOrchestrator.create(); }
    @AfterEach  void tearDown() { orch.close(); }

    @Test
    void discoversEveryPublicBooleanFieldOnRealGamepad() throws Exception {
        Set<String> expectedButtons = collectPublicFieldsOfType(boolean.class);
        Set<String> expectedAxes    = collectPublicFieldsOfType(float.class);

        assertEquals(27, expectedButtons.size(),
                "expected 27 public boolean fields on real SDK Gamepad, got " + expectedButtons);
        assertEquals(10, expectedAxes.size(),
                "expected 10 public float fields on real SDK Gamepad, got " + expectedAxes);

        Gamepad gp = new Gamepad();
        GamepadAdaptor.attach(orch, gp, "pad");

        // Pre-create topics for every field.
        Set<String> createdButtonTopics = new HashSet<>();
        Set<String> createdAxisTopics = new HashSet<>();
        for (String f : expectedButtons) {
            String base = "pad/" + f;
            if (orch.findTopic(base, Boolean.class).isPresent()) createdButtonTopics.add(f);
            if (orch.findTopic(base + "/rising", Boolean.class).isPresent()) createdButtonTopics.add(f);
            if (orch.findTopic(base + "/falling", Boolean.class).isPresent()) createdButtonTopics.add(f);
        }
        for (String f : expectedAxes) {
            if (orch.findTopic("pad/" + f, Float.class).isPresent()) createdAxisTopics.add(f);
        }

        assertEquals(expectedButtons, createdButtonTopics,
                "every public boolean field should get a topic");
        assertEquals(expectedAxes, createdAxisTopics,
                "every public float field should get a topic");
    }

    @Test
    void publishesRealSdkButtonNames() throws Exception {
        Gamepad gp = new Gamepad();
        GamepadAdaptor.attach(orch, gp, "pad");

        // Confirm at least one field per "category" from the real SDK round-trips.
        for (String name : new String[]{
                "a", "b", "x", "y", "circle", "cross", "square", "triangle",
                "dpad_up", "dpad_down", "dpad_left", "dpad_right",
                "left_bumper", "right_bumper", "start", "back",
                "left_trigger_pressed", "right_trigger_pressed",
                "touchpad", "touchpad_finger_1", "touchpad_finger_2"}) {
            assertTrue(orch.findTopic("pad/" + name, Boolean.class).isPresent(),
                    "topic for real SDK boolean '" + name + "' should exist");
        }

        for (String name : new String[]{
                "left_stick_x", "left_stick_y", "right_stick_x", "right_stick_y",
                "left_trigger", "right_trigger",
                "touchpad_finger_1_x", "touchpad_finger_1_y",
                "touchpad_finger_2_x", "touchpad_finger_2_y"}) {
            assertTrue(orch.findTopic("pad/" + name, Float.class).isPresent(),
                    "topic for real SDK float '" + name + "' should exist");
        }
    }

    @Test
    void newSdkFieldsAreAutoPickedUp() throws Exception {
        // SDK 11.2 added `left_trigger_pressed` and `right_trigger_pressed` which
        // were not in SDK 11.0. Our reflection enumerates *all* public boolean
        // fields, so the new ones should appear automatically.
        Set<String> expectedButtons = collectPublicFieldsOfType(boolean.class);
        assertTrue(expectedButtons.contains("left_trigger_pressed"));
        assertTrue(expectedButtons.contains("right_trigger_pressed"));
    }

    @Test
    void nonBooleanNonFloatFieldsAreSkipped() {
        // id (int), timestamp (long), and nextRumbleApproxFinishTime (long) are
        // public but not boolean or float — they should NOT generate topics.
        Gamepad gp = new Gamepad();
        GamepadAdaptor.attach(orch, gp, "pad");

        assertTrue(orch.findTopic("pad/id", Integer.class).isEmpty(),
                "int field should not generate a topic");
        assertTrue(orch.findTopic("pad/timestamp", Long.class).isEmpty(),
                "long field should not generate a topic");
        assertTrue(orch.findTopic("pad/nextRumbleApproxFinishTime", Long.class).isEmpty(),
                "long field should not generate a topic");
    }

    @Test
    void mutationTriggersRisingAndFalling() throws Exception {
        Gamepad gp = new Gamepad();
        GamepadAdaptor.attach(orch, gp, "pad");

        List<Boolean> rising = new CopyOnWriteArrayList<>();
        List<Boolean> falling = new CopyOnWriteArrayList<>();
        List<Float> stickX = new CopyOnWriteArrayList<>();

        orch.subscribe("pad/right_bumper/rising", Boolean.class, rising::add);
        orch.subscribe("pad/right_bumper/falling", Boolean.class, falling::add);
        orch.subscribe("pad/left_stick_x", Float.class, stickX::add);

        gp.right_bumper = true;
        gp.left_stick_x = 0.75f;
        Thread.sleep(80); // ~5 poll cycles

        gp.right_bumper = false;
        Thread.sleep(80);

        boolean gotRising = false, gotFalling = false;
        for (int i = 0; i < 200 && !(gotRising && gotFalling); i++) {
            gotRising |= !rising.isEmpty();
            gotFalling |= !falling.isEmpty();
            if (gotRising && gotFalling) break;
            Thread.sleep(10);
        }

        assertTrue(gotRising, "expected a rising edge on right_bumper; rising=" + rising);
        assertTrue(gotFalling, "expected a falling edge on right_bumper; falling=" + falling);
        assertTrue(stickX.stream().anyMatch(v -> Math.abs(v - 0.75f) < 0.001f),
                "expected left_stick_x = 0.75 to be published; got " + stickX);
    }

    // ----------------------------------------------------------------------

    private static Set<String> collectPublicFieldsOfType(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Field f : Gamepad.class.getFields()) {
            // GamepadAdaptor skips static fields, so the test helper must too.
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == type) names.add(f.getName());
        }
        return names;
    }
}
