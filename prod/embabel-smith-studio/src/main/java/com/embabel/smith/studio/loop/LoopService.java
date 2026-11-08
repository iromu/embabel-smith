package com.embabel.smith.studio.loop;

import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.improve.ImprovementLoop;
import com.embabel.smith.studio.improve.PendingCandidate;
import com.embabel.smith.studio.rest.dto.ApproveResponse;
import com.embabel.smith.studio.rest.dto.IterationInfo;
import com.embabel.smith.studio.rest.dto.LoopStatus;
import com.embabel.smith.studio.rest.dto.RejectResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The studio's control surface over the self-improvement loop (change 06): runs iterations (on
 * demand from the UI or from the interval {@link LoopScheduler}), starts/stops the schedule,
 * toggles auto-publish, and approves/rejects pending candidates.
 *
 * <p>The loop is always constructed in <em>hold</em> mode, so every gated candidate lands in the
 * approval queue. Auto-publish is implemented at this layer: when the flag is on, each iteration
 * immediately approves (publishes) the candidates it produced, so they never sit pending. This
 * keeps the change-05 loop untouched while making the toggle effective at runtime.
 */
@Service
public class LoopService {

    private final ImprovementLoop loop;
    private final LoopSchedulerState state;
    private final Publisher publisher;
    private final ModelEndpointConfig endpoint;

    public LoopService(ImprovementLoop loop, LoopSchedulerState state, Publisher publisher,
                       ModelEndpointConfig endpoint) {
        this.loop = loop;
        this.state = state;
        this.publisher = publisher;
        this.endpoint = endpoint;
    }

    /**
     * Runs one iteration: the loop pass over the improve source, then — when auto-publish is on —
     * immediately approves every candidate it produced. A missing model endpoint is reported
     * (structured {@code MODEL_NOT_CONFIGURED}) without running, since the iteration's re-eval
     * spends model calls.
     */
    public IterationInfo trigger() {
        String configError = endpoint.configError();
        if (configError != null) {
            return new IterationInfo(false, 0, List.of(), List.of(), state.autoPublish(),
                    "MODEL_NOT_CONFIGURED", configError);
        }
        List<PendingCandidate> produced = loop.runOnce();
        List<String> published = new ArrayList<>();
        List<String> queued = new ArrayList<>();
        if (state.autoPublish()) {
            for (PendingCandidate candidate : produced) {
                try {
                    loop.queue().approve(candidate.id(), this::publish);
                    published.add(candidate.skill());
                } catch (RuntimeException e) {
                    queued.add(candidate.skill()); // publish failed; leave it pending for a manual decision
                }
            }
        } else {
            for (PendingCandidate candidate : produced) {
                queued.add(candidate.skill());
            }
        }
        return new IterationInfo(true, produced.size(), published, queued, state.autoPublish(), null, null);
    }

    /** Starts the interval schedule. */
    public LoopStatus start() {
        state.start();
        return status();
    }

    /** Stops the interval schedule. */
    public LoopStatus stop() {
        state.stop();
        return status();
    }

    /** The current schedule + auto-publish status. */
    public LoopStatus status() {
        return new LoopStatus(state.running(), state.autoPublish());
    }

    /** The pending candidates awaiting a decision. */
    public List<PendingCandidate> candidates() {
        return loop.queue().pending();
    }

    /** Sets the auto-publish mode. */
    public LoopStatus setAutoPublish(boolean autoPublish) {
        state.setAutoPublish(autoPublish);
        return status();
    }

    /** Approves (publishes) a pending candidate. */
    public ApproveResponse approve(String id) {
        try {
            PublishResult result = loop.queue().approve(id, this::publish);
            return new ApproveResponse(true, result.location().toString(), null);
        } catch (IllegalArgumentException e) {
            return new ApproveResponse(false, null, "No pending candidate with id " + id);
        }
    }

    /** Rejects (discards) a pending candidate. */
    public RejectResponse reject(String id) {
        try {
            loop.queue().reject(id);
            return new RejectResponse(true, null);
        } catch (IllegalArgumentException e) {
            return new RejectResponse(false, "No pending candidate with id " + id);
        }
    }

    /** Publishes a candidate through the shared publisher (the loop's write path). */
    private PublishResult publish(PendingCandidate candidate) {
        try {
            return publisher.publish(candidate.candidate(), candidate.proposedLifecycle(), true, false);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "cannot publish candidate for " + candidate.skill(), e);
        }
    }
}
