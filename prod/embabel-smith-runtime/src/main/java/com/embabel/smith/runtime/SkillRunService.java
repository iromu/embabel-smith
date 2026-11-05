package com.embabel.smith.runtime;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.core.GoalNotFoundException;
import com.embabel.smith.runtime.core.IngestedSource;
import com.embabel.smith.runtime.core.RunOutcome;
import com.embabel.smith.runtime.core.RuntimeCore;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.core.TrustTierBlockedException;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.runtime.trace.TraceRecorder;
import com.embabel.smith.skillsource.ResolvedSkillSource;
import com.embabel.smith.skillsource.SkillSource;

import java.nio.file.Path;

/**
 * The product run service: source URI in, run outcome (plus its trace record) out.
 *
 * <p>Plain class — no Spring — so the CLI and the studio (change 06) both embed it. Flow:
 * parse + resolve the skill source (change 02), ingest all packs (POC 1/6), run the goal with
 * planner selection and trust-tier enforcement (POC 1/10), and append the trace record.
 */
public class SkillRunService {

    /** The outcome of a run: success, the goal output, the failure reason, and the emitted trace. */
    public record RunResult(boolean success, String output, String error, TraceRecord trace) {
    }

    private final RuntimeCore core;
    private final TraceRecorder recorder;

    /** Uses the default trace store ({@code ~/.smith/traces/runs.jsonl}). */
    public SkillRunService(AgentPlatform platform) {
        this(platform, JsonlTraceStore.defaultPath());
    }

    public SkillRunService(AgentPlatform platform, Path traceFile) {
        this.core = new RuntimeCore(platform);
        this.recorder = new TraceRecorder(new JsonlTraceStore(traceFile));
    }

    /**
     * Runs the named goal over the packs resolved from {@code sourceUri}. When {@code goal} is
     * {@code null}, the single exported goal is used (an error lists the goals when there are
     * several). A trace record is appended for every run attempt, success or failure.
     */
    public RunResult run(String sourceUri, String goal, String input,
                         String plannerOverride, TrustTier tier) {
        ResolvedSkillSource resolved = SkillSource.parse(sourceUri).resolve();
        IngestedSource ingested = core.ingest(resolved);
        String effectiveGoal = goal == null || goal.isBlank() ? singleGoal(ingested) : goal;

        RunOutcome outcome;
        try {
            outcome = core.run(ingested, effectiveGoal, input == null ? "" : input, plannerOverride, tier);
        } catch (GoalNotFoundException | TrustTierBlockedException e) {
            outcome = RunOutcome.failed(e.getMessage());
        }

        TraceRecord record = recorder.record(ingested, effectiveGoal, input == null ? "" : input, tier, outcome);
        return new RunResult(outcome.success(), outcome.output(), outcome.error(), record);
    }

    private String singleGoal(IngestedSource ingested) {
        var goals = ingested.goalNames();
        if (goals.size() == 1) {
            return goals.get(0);
        }
        throw new IllegalStateException("The source exports multiple goals: " + goals
                + ". Name one explicitly to run it.");
    }
}
