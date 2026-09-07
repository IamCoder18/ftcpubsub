package com.aaravlabs.synapse;

/**
 * Where log messages get sent. Pluggable so the same core can log to {@code System.err}
 * during tests and to {@code android.util.Log} on the robot.
 */
public interface LogSink {

    void info(String tag, String message);

    void warn(String tag, String message);

    void error(String tag, String message);

    void error(String tag, String message, Throwable t);

    /** A sink that writes everything to {@code System.err}. Useful for tests. */
    LogSink STDERR = new LogSink() {
        @Override public void info(String tag, String message) { System.err.printf("[INFO] %s: %s%n", tag, message); }
        @Override public void warn(String tag, String message) { System.err.printf("[WARN] %s: %s%n", tag, message); }
        @Override public void error(String tag, String message) { System.err.printf("[ERR ] %s: %s%n", tag, message); }
        @Override public void error(String tag, String message, Throwable t) {
            System.err.printf("[ERR ] %s: %s%n", tag, message);
            t.printStackTrace(System.err);
        }
    };

    /** A sink that silently drops everything. */
    LogSink SILENT = new LogSink() {
        @Override public void info(String tag, String message) {}
        @Override public void warn(String tag, String message) {}
        @Override public void error(String tag, String message) {}
        @Override public void error(String tag, String message, Throwable t) {}
    };
}
