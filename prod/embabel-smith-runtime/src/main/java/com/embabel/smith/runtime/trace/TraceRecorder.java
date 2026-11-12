package com.embabel.smith.runtime.trace;

import com.embabel.agent.core.ActionInvocation;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.core.IngestedSource;
import com.embabel.smith.runtime.core.RunOutcome;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the per-run {@link TraceRecord} from a run outcome and appends it to the trace store.
 *
 * <p>Per-step capture uses the seam pinned by the task-1.1 spike: step names and durations from
 * {@link AgentProcess#getHistory()}, per-step output excerpts from the blackboard binding of the
 * step's declared output type (dynamic types are bound under their decapitalized short name),
 * and the overall outcome from process status and {@code lastResult()}. Tracing observes, never
 * intervenes — it only reads state after the run finished.
 */
public class TraceRecorder {

    /** Excerpts are truncated to this many characters (traces serve the improvement loop and the UI). */
    public static final int DEFAULT_EXCERPT_LIMIT = 500;

    private final JsonlTraceStore store;
    private final int excerptLimit;

    public TraceRecorder(JsonlTraceStore store) {
        this(store, DEFAULT_EXCERPT_LIMIT);
    }

    public TraceRecorder(JsonlTraceStore store, int excerptLimit) {
        this.store = store;
        this.excerptLimit = excerptLimit;
    }

    /** Builds the record for the outcome, appends it to the store, and returns it. */
    public TraceRecord record(IngestedSource ingested, String goal, String input,
                              TrustTier tier, RunOutcome outcome) {
        return record(ingested.source().origin().uri(), ingested.source(), ingested, goal, input, tier, outcome);
    }

    /**
     * Builds the record for the outcome, tolerating a run that failed before resolution or
     * ingestion ({@code resolved}/{@code ingested} may be {@code null}), appends it to the
     * store, and returns it.
     */
    public TraceRecord record(String sourceUri, ResolvedSpecSource resolved, IngestedSource ingested,
                              String goal, String input, TrustTier tier, RunOutcome outcome) {
        TraceRecord record = build(sourceUri, resolved, ingested, goal, input, tier, outcome);
        store.append(record);
        return record;
    }

    TraceRecord build(String sourceUri, ResolvedSpecSource resolved, IngestedSource ingested,
                      String goal, String input, TrustTier tier, RunOutcome outcome) {
        String uri = resolved != null ? resolved.origin().uri() : sourceUri;
        String ref = null;
        String resolvedAt = null;
        if (resolved != null && uri.startsWith("git:")) {
            ref = pinnedRef(uri);
            resolvedAt = resolvedCommit(resolved.origin());
        }

        TraceRecord.PackRef pack = null;
        if (ingested != null) {
            try {
                AgentSpec goalPack = ingested.packForGoal(goal);
                ResolvedSpec resolvedPack = ingested.source().packs().stream()
                        .filter(candidate -> candidate.name().equals(goalPack.name()))
                        .findFirst()
                        .orElse(null);
                if (resolvedPack != null) {
                    pack = new TraceRecord.PackRef(resolvedPack.name(), resolvedPack.contentHash());
                }
            } catch (RuntimeException ignored) {
                // goal not exported by any pack — the trace records the run without a pack
            }
        }

        return new TraceRecord(
                UUID.randomUUID().toString(),
                Instant.now(),
                new TraceRecord.SourceRef(uri, ref, resolvedAt),
                pack,
                goal,
                input,
                outcome.plannerName(),
                tier.name().toLowerCase(),
                steps(ingested, outcome),
                outcome.success(),
                outcome.error(),
                outputExcerpt(outcome));
    }

    private List<TraceRecord.StepRecord> steps(IngestedSource ingested, RunOutcome outcome) {
        AgentProcess process = outcome.process();
        if (process == null) {
            return List.of();
        }
        Map<String, String> stepOutputTypes = new LinkedHashMap<>();
        if (ingested != null) {
            for (AgentSpec pack : ingested.packs()) {
                for (ActionStepSpec step : pack.steps()) {
                    stepOutputTypes.putIfAbsent(step.name(), step.outputTypeName());
                }
            }
        }

        List<ActionInvocation> history = process.getHistory();
        boolean failed = process.getStatus() == AgentProcessStatusCode.FAILED;
        List<TraceRecord.StepRecord> records = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            ActionInvocation invocation = history.get(i);
            String status = failed && i == history.size() - 1 ? "failed" : "completed";
            String excerpt = stepOutputExcerpt(process, stepOutputTypes.get(invocation.getActionName()));
            records.add(new TraceRecord.StepRecord(
                    invocation.getActionName(), status, invocation.getRunningTime().toMillis(), excerpt));
        }
        return records;
    }

    /** The last blackboard value bound to the step's output-type variable, excerpted. */
    private String stepOutputExcerpt(AgentProcess process, String outputTypeName) {
        if (outputTypeName == null) {
            return null;
        }
        Object value = process.getBlackboard().get(decapitalize(shortName(outputTypeName)));
        return value == null ? null : excerpt(String.valueOf(value));
    }

    private String outputExcerpt(RunOutcome outcome) {
        if (!outcome.success() || outcome.process() == null) {
            return null;
        }
        Object last = outcome.process().lastResult();
        String value = last instanceof BackgroundMessage message ? message.getContent() : String.valueOf(last);
        return excerpt(value);
    }

    private String excerpt(String value) {
        return truncate(value, excerptLimit);
    }

    /** Truncates the value to at most {@code limit} characters (traces are excerpts, not archives). */
    static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    /** The pinned ref of a {@code git:<url>[@<ref>]} URI, or {@code null} when unpinned. */
    private static String pinnedRef(String uri) {
        String rest = uri.substring("git:".length());
        int at = rest.lastIndexOf('@');
        return at < 0 ? null : rest.substring(at + 1);
    }

    /** The commit SHA the git source materialized at, or {@code null} when it cannot be read. */
    private static String resolvedCommit(ResolvedSpecSource.Origin origin) {
        try {
            Process process = new ProcessBuilder("git", "-C", origin.root().toString(), "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(process.getInputStream().readAllBytes()).trim();
            if (process.waitFor() != 0 || out.isBlank()) {
                return null;
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortName(String typeName) {
        int idx = typeName.lastIndexOf('.');
        return idx >= 0 ? typeName.substring(idx + 1) : typeName;
    }

    private static String decapitalize(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
