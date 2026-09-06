package com.aaravlabs.pubsub.internal;

import com.aaravlabs.pubsub.OrchestratorImpl;
import com.aaravlabs.pubsub.Node;
import com.aaravlabs.pubsub.Subscription;
import com.aaravlabs.pubsub.annotation.OnHardwareThread;
import com.aaravlabs.pubsub.annotation.RunnableAction;
import com.aaravlabs.pubsub.annotation.RunPeriodically;
import com.aaravlabs.pubsub.annotation.SubscribedTo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Scans a {@link Node}'s class for annotated methods and wires them up to the
 * orchestrator. Package-private; called by {@link OrchestratorImpl#registerNode}.
 */
public final class AnnotationBinder {

    private AnnotationBinder() {}

    public static void bindNode(OrchestratorImpl orch, Node node, String nodeName) {
        for (Method m : allDeclaredMethods(node.getClass())) {
            wireRunPeriodically(orch, node, m);
            wireSubscribedTos(orch, node, m);
            wireRunnableAction(orch, node, m);
        }
    }

    public static void unbindNode(OrchestratorImpl orch, Node node) {
        orch.cleanupNode(node);
    }

    // ----------------------------------------------------------------------
    // @RunPeriodically
    // ----------------------------------------------------------------------

    private static void wireRunPeriodically(OrchestratorImpl orch, Node node, Method m) {
        RunPeriodically[] rps = m.getAnnotationsByType(RunPeriodically.class);
        if (rps.length == 0) return;

        if (m.getParameterCount() != 0) {
            throw new IllegalArgumentException(
                    "@RunPeriodically method must take no parameters: " + m);
        }
        if (Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException(
                    "@RunPeriodically method must not be static: " + m);
        }

        m.setAccessible(true);
        for (RunPeriodically rp : rps) {
            if (rp.hz() <= 0) {
                orch.warn("@RunPeriodically hz must be > 0 on " + m);
                continue;
            }
            long delayMs = Math.max(1, 1000L / rp.hz());

            ScheduledFuture<?> f;
            if (rp.hardware()) {
                f = orch.scheduleHardwarePeriodic(() -> {
                    try {
                        m.invoke(node);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                        orch.error("@RunPeriodically(hardware) method threw: " + m, cause);
                        throw new RuntimeException(cause);
                    } catch (Throwable t) {
                        orch.error("@RunPeriodically(hardware) method threw: " + m, t);
                        throw new RuntimeException(t);
                    }
                }, delayMs);
            } else {
                f = orch.schedulePeriodic(() -> {
                    try {
                        m.invoke(node);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                        orch.error("@RunPeriodically method threw: " + m, cause);
                        throw new RuntimeException(cause);
                    } catch (Throwable t) {
                        orch.error("@RunPeriodically method threw: " + m, t);
                        throw new RuntimeException(t);
                    }
                }, delayMs);
            }
            orch.trackNodeScheduled(node, f);
        }
    }

    // ----------------------------------------------------------------------
    // @SubscribedTo  (with optional @OnHardwareThread)
    // ----------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void wireSubscribedTos(OrchestratorImpl orch, Node node, Method m) {
        SubscribedTo[] subs = m.getAnnotationsByType(SubscribedTo.class);
        if (subs.length == 0) return;

        if (m.getParameterCount() > 1) {
            throw new IllegalArgumentException(
                    "@SubscribedTo method must take 0 or 1 parameter: " + m);
        }

        Class<?> paramType = m.getParameterCount() == 1 ? m.getParameterTypes()[0] : null;
        Class<?> topicType = paramType != null ? boxed(paramType) : Object.class;

        m.setAccessible(true);

        boolean onHardware = m.isAnnotationPresent(OnHardwareThread.class);
        Runnable handlerBody = () -> {
            try {
                // Body is invoked by the dispatcher (callback pool or hardware
                // thread). We capture paramType via a closure.
                // The actual subscription handler is built per-topic below.
            } catch (Throwable t) {
                orch.error("@SubscribedTo handler threw: " + m, t);
            }
        };
        // We don't actually use handlerBody above — each topic gets its own
        // handler that captures its own message type. The annotation-present
        // check is the only thing we need from here.

        for (SubscribedTo sub : subs) {
            Subscription s = orch.subscribeRaw(sub.topic(), topicType, msg -> {
                try {
                    if (paramType == null) {
                        m.invoke(node);
                    } else if (paramType.isPrimitive() ? boxed(paramType).isInstance(msg)
                                                       : paramType.isInstance(msg)) {
                        m.invoke(node, msg);
                    }
                    // else: silently drop — message type didn't match the parameter.
                } catch (InvocationTargetException ite) {
                    orch.error("@SubscribedTo handler threw: " + m,
                            ite.getCause() != null ? ite.getCause() : ite);
                } catch (Throwable t) {
                    orch.error("@SubscribedTo handler threw: " + m, t);
                }
            });
            // If @OnHardwareThread is present, re-route this subscription's
            // handler to the hardware thread. We do this by wrapping the
            // subscription in a special wrapper that, when invoked, submits
            // work to hardwareThread instead of the callback pool.
            if (onHardware) {
                orch.markSubscriptionAsHardwareThreaded(s);
            }
            orch.trackNodeSubscription(node, s);
        }
    }

    private static Class<?> boxed(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class)     return Integer.class;
        if (c == long.class)    return Long.class;
        if (c == double.class)  return Double.class;
        if (c == float.class)   return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class)    return Byte.class;
        if (c == short.class)   return Short.class;
        if (c == char.class)    return Character.class;
        return c;
    }

    // ----------------------------------------------------------------------
    // @RunnableAction
    // ----------------------------------------------------------------------

    private static void wireRunnableAction(OrchestratorImpl orch, Node node, Method m) {
        RunnableAction ra = m.getAnnotation(RunnableAction.class);
        if (ra == null) return;
        if (m.getParameterCount() != 0) {
            throw new IllegalArgumentException(
                    "@RunnableAction method must take no parameters: " + m);
        }
        if (m.getReturnType() != void.class) {
            throw new IllegalArgumentException(
                    "@RunnableAction method must return void: " + m);
        }
        if (Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException(
                    "@RunnableAction method must not be static: " + m);
        }
        m.setAccessible(true);
        orch.registerAction(node, ra.value(), m);
        orch.trackNodeAction(node, ra.value());
    }

    // ----------------------------------------------------------------------

    /** Walk the class hierarchy to find inherited annotated methods. */
    private static List<Method> allDeclaredMethods(Class<?> cls) {
        List<Method> out = new ArrayList<>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) out.add(m);
            c = c.getSuperclass();
        }
        return out;
    }
}
