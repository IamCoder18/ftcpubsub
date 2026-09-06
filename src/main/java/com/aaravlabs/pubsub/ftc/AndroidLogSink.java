package com.aaravlabs.pubsub.ftc;

import com.aaravlabs.pubsub.LogSink;

/**
 * A {@link LogSink} that forwards messages to {@code android.util.Log}. Implemented via
 * reflection so this class can be compiled without the Android SDK on the build
 * classpath (the FTC SDK is added at runtime by the consumer).
 *
 * <p>On non-Android JVMs (e.g. unit tests) the methods silently no-op.
 */
public final class AndroidLogSink implements LogSink {

    private static final Class<?> LOG_CLASS = resolve("android.util.Log");

    @Override
    public void info(String tag, String message) {
        invoke("i", tag, message);
    }

    @Override
    public void warn(String tag, String message) {
        invoke("w", tag, message);
    }

    @Override
    public void error(String tag, String message) {
        invoke("e", tag, message);
    }

    @Override
    public void error(String tag, String message, Throwable t) {
        invoke("e", tag, message);
        if (t != null) {
            for (StackTraceElement el : t.getStackTrace()) {
                invoke("e", tag, "    at " + el);
            }
        }
    }

    private static void invoke(String method, String tag, String message) {
        if (LOG_CLASS == null) return;
        try {
            LOG_CLASS.getMethod(method, String.class, String.class)
                    .invoke(null, tag, message);
        } catch (Throwable ignored) {
            // best-effort logging; never let logging itself crash the robot
        }
    }

    private static Class<?> resolve(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
