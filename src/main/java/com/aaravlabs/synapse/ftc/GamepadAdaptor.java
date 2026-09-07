package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Node;
import com.aaravlabs.synapse.Orchestrator;
import com.aaravlabs.synapse.annotation.RunPeriodically;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pushes gamepad state onto the bus at a fixed rate. For an FTC gamepad with parent
 * topic {@code "gamepad1"}, the following topics are populated:
 *
 * <ul>
 *   <li>{@code gamepad1/<button>} — the current state of each boolean field (e.g.
 *       {@code a}, {@code right_bumper}, {@code dpad_up}).</li>
 *   <li>{@code gamepad1/<button>/rising} — fires (with value {@code true}) on a
 *       0→1 transition.</li>
 *   <li>{@code gamepad1/<button>/falling} — fires on a 1→0 transition.</li>
 *   <li>{@code gamepad1/<axis>} — the current value of each float field (e.g.
 *       {@code left_stick_x}, {@code right_trigger}).</li>
 * </ul>
 *
 * <p>The parent topic is created implicitly on first publish. Polling is at
 * {@link #HZ} hertz; axes and buttons share the same loop for simplicity.
 *
 * <p>Usage:
 * <pre>{@code
 * GamepadAdaptor.attach(orch, gamepad1, "gamepad1");
 *
 * // Anywhere:
 * orch.subscribe("gamepad1/right_bumper/rising", Boolean.class, _ -> {
 *     orch.publish("intake/set/power", 1.0);
 * });
 * }</pre>
 */
public final class GamepadAdaptor extends Node {

    /** Polling frequency. 60 Hz = 16 ms latency, fine for FTC. */
    public static final int HZ = 60;

    private final Object gamepad;
    private final String parentTopic;
    private final List<Field> buttonFields;
    private final List<Field> axisFields;
    private final boolean[] lastButton;
    private final boolean gamepadResolved;

    private GamepadAdaptor(Orchestrator orch, Object gamepad, String parentTopic) {
        super(orch);
        this.gamepad = gamepad;
        this.parentTopic = parentTopic;
        this.gamepadResolved = (gamepad != null);

        if (gamepadResolved) {
            this.buttonFields = new ArrayList<>();
            this.axisFields = new ArrayList<>();
            for (Field f : gamepad.getClass().getFields()) {
                // Skip static fields (e.g. SDK's public static final constants like
                // DEFAULT_TRIGGER_THRESHOLD). They wouldn't have per-instance values.
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == boolean.class) buttonFields.add(f);
                else if (f.getType() == float.class) axisFields.add(f);
            }
            this.lastButton = new boolean[buttonFields.size()];
        } else {
            this.buttonFields = java.util.Collections.emptyList();
            this.axisFields = java.util.Collections.emptyList();
            this.lastButton = new boolean[0];
        }

        // Pre-create the topics so consumers can subscribe before the first tick.
        for (Field f : buttonFields) {
            orch.getOrCreateTopic(topic(f.getName()), Boolean.class);
            orch.getOrCreateTopic(topic(f.getName() + "/rising"), Boolean.class);
            orch.getOrCreateTopic(topic(f.getName() + "/falling"), Boolean.class);
        }
        for (Field f : axisFields) {
            orch.getOrCreateTopic(topic(f.getName()), Float.class);
        }
    }

    private String topic(String suffix) {
        return parentTopic + "/" + suffix;
    }

    /**
     * Attach an adaptor that polls {@code gamepad} and dispatches its state onto
     * {@code parentTopic}. Returns the registered node name.
     */
    public static String attach(Orchestrator orch, Object gamepad, String parentTopic) {
        String name = "GamepadAdaptor:" + parentTopic;
        GamepadAdaptor adaptor = new GamepadAdaptor(orch, gamepad, parentTopic);
        orch.registerNode(name, adaptor);
        return name;
    }

    @RunPeriodically(hz = HZ)
    public void poll() {
        if (!gamepadResolved) return;
        try {
            // Axes.
            for (Field f : axisFields) {
                orchestrator.publish(topic(f.getName()), f.getFloat(gamepad));
            }
            // Buttons (with edge detection).
            for (int i = 0; i < buttonFields.size(); i++) {
                Field f = buttonFields.get(i);
                boolean now = f.getBoolean(gamepad);
                boolean was = lastButton[i];
                orchestrator.publish(topic(f.getName()), now);
                if (now && !was) {
                    orchestrator.publish(topic(f.getName() + "/rising"), true);
                } else if (!now && was) {
                    orchestrator.publish(topic(f.getName() + "/falling"), true);
                }
                lastButton[i] = now;
            }
        } catch (IllegalAccessException e) {
            orchestrator.warn("GamepadAdaptor: field access denied: " + e.getMessage());
        } catch (Throwable t) {
            orchestrator.warn("GamepadAdaptor poll failed: " + t.getMessage());
        }
    }
}
