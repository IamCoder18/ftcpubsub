package com.aaravlabs.synapse.ftc;

import com.aaravlabs.synapse.Orchestrator;

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
    public static Orchestrator create(com.aaravlabs.synapse.LogSink sink) {
        return Orchestrator.create("ftc", sink);
    }
}
