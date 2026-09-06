package com.aaravlabs.pubsub.ftc;

import com.aaravlabs.pubsub.Orchestrator;

/**
 * Static factory methods for the FTC-flavored orchestrator. Uses {@link
 * AndroidLogSink} and the name {@code "ftc"}.
 */
public final class FtcOrchestrator {

    private FtcOrchestrator() {}

    /** Create an orchestrator preconfigured for FTC. */
    public static Orchestrator create() {
        return Orchestrator.create("ftc", new AndroidLogSink());
    }

    /** Create an orchestrator with a custom log sink. */
    public static Orchestrator create(com.aaravlabs.pubsub.LogSink sink) {
        return Orchestrator.create("ftc", sink);
    }
}
