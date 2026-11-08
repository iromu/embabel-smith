package com.embabel.smith.studio.loop;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mutable control state of the self-improvement loop's schedule (change 06): whether the
 * interval schedule is running, and whether gated candidates are auto-published. The schedule is
 * <em>off by default</em> — the loop runs only when started from the UI or triggered on demand.
 *
 * <p>Plain, thread-safe state (no Spring): the {@code @Scheduled} scheduler reads {@link #running()}
 * each tick, and the REST surface flips the flags. The auto-publish flag is consulted by the
 * {@link LoopService} after each iteration, so the toggle takes effect without rebuilding the loop.
 */
public final class LoopSchedulerState {

    private final AtomicBoolean running;
    private final AtomicBoolean autoPublish;

    public LoopSchedulerState(boolean autoPublish) {
        this.running = new AtomicBoolean(false);
        this.autoPublish = new AtomicBoolean(autoPublish);
    }

    /** Whether the interval schedule is currently running (default: false). */
    public boolean running() {
        return running.get();
    }

    /** Starts the schedule (idempotent). */
    public void start() {
        running.set(true);
    }

    /** Stops the schedule (idempotent). */
    public void stop() {
        running.set(false);
    }

    /** Whether gated candidates are auto-published instead of held for approval. */
    public boolean autoPublish() {
        return autoPublish.get();
    }

    /** Sets the auto-publish mode. */
    public void setAutoPublish(boolean autoPublish) {
        this.autoPublish.set(autoPublish);
    }
}
