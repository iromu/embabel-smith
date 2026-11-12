package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.model.AgentSpec;

import java.time.Instant;

/**
 * The lifecycle state machine (POC 18, carried into the product): how a spec's state moves as it
 * is improved and regresses.
 *
 * <pre>
 *   first proven improvement          regression (re-eval &lt; baseline)
 * incubating -----------------------> active -----------------------> degraded
 *                                        ^                               |
 *                                        |                               | next re-eval fails
 *                                        | gated candidate published     v
 *                                        +----------------------------- retired
 * </pre>
 */
public final class LifecycleTransitions {

    private LifecycleTransitions() {
    }

    /**
     * The state a published (gated) candidate moves the pack to: a first proven improvement
     * promotes {@code incubating} to {@code active}; a recovered {@code degraded} pack also returns
     * to {@code active}; an already-{@code active} pack stays {@code active}. (A {@code retired}
     * pack is only ever republished by an explicit human rebuild, which revives it.)
     */
    public static LifecycleState onPublished(LifecycleState current) {
        return switch (current) {
            case INCUBATING, DEGRADED, ACTIVE, RETIRED -> LifecycleState.ACTIVE;
        };
    }

    /**
     * The state a live re-evaluation below baseline moves the pack to: an {@code active} pack
     * degrades; a {@code degraded} pack that fails its next re-evaluation is retired.
     */
    public static LifecycleState onRegression(LifecycleState current) {
        return switch (current) {
            case ACTIVE -> LifecycleState.DEGRADED;
            case DEGRADED -> LifecycleState.RETIRED;
            case INCUBATING, RETIRED -> current;
        };
    }

    /**
     * The sidecar for a published candidate: the new content hash, the new baseline (the
     * candidate's re-eval score), and the state it transitions to.
     */
    public static LifecycleMetadata sidecarFor(AgentSpec candidate, double newBaseline, LifecycleState state) {
        return new LifecycleMetadata(SpecContentHash.of(candidate), newBaseline, state,
                Instant.now().toString());
    }

    /**
     * The sidecar after a regression: the same content hash and baseline, but the state moved per
     * {@link #onRegression} (the baseline is unchanged — the pack's content did not change, only
     * its measured score).
     */
    public static LifecycleMetadata regressed(LifecycleMetadata sidecar) {
        return new LifecycleMetadata(sidecar.contentHash(), sidecar.baselinePassRate(),
                onRegression(sidecar.state()), Instant.now().toString());
    }
}
