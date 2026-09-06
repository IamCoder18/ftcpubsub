package com.aaravlabs.pubsub;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

/**
 * The pub/sub bus. One per robot. Nodes talk to each other by {@link #publish publishing}
 * to and {@link #subscribe subscribing} to named, typed {@link Topic topics}.
 *
 * <p>Threads: the orchestrator runs <em>two</em> internal thread pools so that
 * {@link com.aaravlabs.pubsub.annotation.SubscribedTo} callbacks can never starve
 * {@link com.aaravlabs.pubsub.annotation.RunPeriodically} loops and vice-versa. See the
 * project README for the rationale.
 *
 * <p>Lifecycle: create via {@link #create(String)} (or {@code FtcOrchestrator.create()}
 * on the robot), register nodes with {@link #registerNode(String, Node)}, and call
 * {@link #close()} when the OpMode ends.
 */
public interface Orchestrator extends AutoCloseable {

    // ---- identity ---------------------------------------------------------

    /** Short human-readable name for this orchestrator, mostly for logging. */
    String name();

    // ---- topics -----------------------------------------------------------

    /**
     * Look up an existing topic, or create it with the given type if it does not yet
     * exist. If the topic already exists with a different type, this throws.
     */
    <T> Topic<T> getOrCreateTopic(String name, Class<T> type);

    /** Look up an existing topic by name, regardless of its type. */
    Optional<Topic<?>> findTopic(String name);

    /** Convenience: typed fetch of an existing topic. */
    <T> Optional<Topic<T>> findTopic(String name, Class<T> type);

    // ---- publish ----------------------------------------------------------

    /**
     * Publish {@code value} to the named topic. Subscribers will be invoked
     * asynchronously on the callback pool. The latest value cache is updated
     * synchronously before any callbacks run, so {@link Topic#latestValue()} reflects
     * the new value immediately.
     *
     * @throws IllegalArgumentException if no topic named {@code name} exists yet (call
     *         {@link #getOrCreateTopic} first) or if the value's type does not match.
     */
    <T> void publish(String name, T value);

    // ---- subscribe --------------------------------------------------------

    /**
     * Subscribe {@code handler} to the named topic. The handler is invoked
     * asynchronously on the callback pool for every published value.
     *
     * @return a {@link Subscription} handle that can be used to unsubscribe
     */
    <T> Subscription subscribe(String name, Class<T> type, java.util.function.Consumer<? super T> handler);

    // ---- fetch the latest value ------------------------------------------

    /**
     * Convenience: get the most recently published value on the named topic, or empty
     * if nothing has been published yet. Equivalent to
     * {@code findTopic(name).flatMap(Topic::latestValue)} but type-safe.
     */
    <T> Optional<T> getLatestValue(String name, Class<T> type);

    // ---- nodes -----------------------------------------------------------

    /**
     * Register a node with the orchestrator. This:
     * <ul>
     *   <li>Wires up all {@link com.aaravlabs.pubsub.annotation.SubscribedTo} callbacks.</li>
     *   <li>Schedules all {@link com.aaravlabs.pubsub.annotation.RunPeriodically} loops.</li>
     *   <li>Registers all {@link com.aaravlabs.pubsub.annotation.RunnableAction} names.</li>
     * </ul>
     * The node is stored under {@code name}; calling {@link #registerNode} again with
     * the same name is a no-op.
     */
    Node registerNode(String name, Node node);

    /** Stop and unregister a previously registered node. */
    void unregisterNode(String name);

    /** Look up a registered node by name. */
    Optional<Node> findNode(String name);

    // ---- actions ---------------------------------------------------------

    /**
     * Fire a registered {@link com.aaravlabs.pubsub.annotation.RunnableAction} by name.
     * The returned future completes when the action's method returns normally, or
     * completes exceptionally if the method throws.
     */
    CompletableFuture<Void> runAction(String actionName);

    /** Cancel all currently-running actions. */
    void cancelAllActions();

    // ---- scheduling helpers ---------------------------------------------

    /**
     * Schedule {@code task} to run periodically on the scheduler pool at {@code hz}
     * hertz. Exposed for users who don't want the annotation form.
     */
    ScheduledFuture<?> runPeriodically(Runnable task, int hz);

    // ---- hardware thread -------------------------------------------------

    /**
     * Run {@code task} on the orchestrator's <b>dedicated single hardware
     * thread</b>. All {@code runOnHardwareThread} invocations and all
     * {@link com.aaravlabs.pubsub.annotation.OnHardwareThread}-annotated callbacks
     * share this thread and execute strictly serially.
     *
     * <p>Use this when you need to touch FTC hardware from non-annotated code,
     * for example from inside an OpMode lifecycle hook.
     *
     * <p>If you call {@link #publish} from the runnable, the publish itself is
     * non-blocking (the bus enqueues the callback-pool task). The publish does
     * not block the hardware thread.
     */
    void runOnHardwareThread(Runnable task);

    /**
     * Returns a high-level facade over the hardware thread: {@code run} (async),
     * {@code call} (sync, blocking, returns a value), {@code callAsync}
     * (returns a {@link java.util.concurrent.CompletableFuture}), and
     * {@code bulkRead} (periodic read pattern that publishes values to topics).
     *
     * <p>Most users should use this facade rather than {@link #runOnHardwareThread}
     * directly — it gives you synchronous read access to hardware without breaking
     * thread safety.
     */
    com.aaravlabs.pubsub.ftc.HardwareActions hardware();

    // ---- logging ---------------------------------------------------------

    /** Send an info-level log message tagged with this orchestrator's name. */
    void log(String message);

    void log(String tag, String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable t);

    // ---- lifecycle -------------------------------------------------------

    /** True if {@link #close()} has been called. */
    boolean isClosed();

    /**
     * Stop all periodic loops, cancel all actions, shut down both thread pools. Safe
     * to call more than once.
     */
    @Override
    void close();

    // ---- factories -------------------------------------------------------

    /** Create a basic orchestrator with the given name and the stderr log sink. */
    static Orchestrator create(String name) {
        return OrchestratorImpl.create(name, LogSink.STDERR);
    }

    /** Create a basic orchestrator with the given name and a custom log sink. */
    static Orchestrator create(String name, LogSink logSink) {
        return OrchestratorImpl.create(name, logSink);
    }
}
