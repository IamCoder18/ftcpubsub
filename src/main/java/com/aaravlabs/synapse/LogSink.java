package com.aaravlabs.synapse;

/**
 * Where log messages get sent. Pluggable so the same core can log to {@code System.err}
 * during tests and to {@code android.util.Log} on the robot.
 */
public interface LogSink {

    /**
     * Log an informational message.
     *
     * @param tag short label identifying the source (usually the orchestrator name)
     * @param message the message
     */
    void info(String tag, String message);

    /**
     * Log a warning.
     *
     * @param tag short label identifying the source
     * @param message the message
     */
    void warn(String tag, String message);

    /**
     * Log an error.
     *
     * @param tag short label identifying the source
     * @param message the message
     */
    void error(String tag, String message);

    /**
     * Log an error with a stack trace.
     *
     * @param tag short label identifying the source
     * @param message the message
     * @param t the throwable whose stack trace should be printed
     */
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
