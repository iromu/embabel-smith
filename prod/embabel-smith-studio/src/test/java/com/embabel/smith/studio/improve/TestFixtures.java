package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SkillContentHash;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.runtime.trace.TraceRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Shared fixtures for the self-improvement-loop tests. */
final class TestFixtures {

    private TestFixtures() {
    }

    /**
     * A valid pack: an exported goal and one action step per name, with a plain prompt (no
     * {@code {{var}}} references, framework I/O types only), so it passes D5 validation and the
     * security scan.
     */
    static SkillPack pack(String name, String... stepNames) {
        return new SkillPack(new SkillPackManifest(name, "1.0.0"),
                GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"),
                steps(name, stepNames), List.of());
    }

    /** A valid pack whose single step carries a custom (possibly unsafe) prompt. */
    static SkillPack packWithPrompt(String name, String stepName, String prompt) {
        List<ActionStepSpec> steps = List.of(new ActionStepSpec(
                ActionStepSpec.STEP_TYPE, stepName, "step " + stepName,
                List.of("UserInput"), "BackgroundMessage", prompt,
                null, null, null, null, null, null, null));
        return new SkillPack(new SkillPackManifest(name, "1.0.0"),
                GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"),
                steps, List.of());
    }

    private static List<ActionStepSpec> steps(String name, String... stepNames) {
        List<ActionStepSpec> out = new ArrayList<>();
        for (String step : stepNames) {
            out.add(ActionStepSpec.action(step, "step " + step, List.of("UserInput"),
                    "BackgroundMessage", "perform " + step + " carefully"));
        }
        return out;
    }

    /** A sidecar for a pack at a given baseline and state (content hash computed from the pack). */
    static LifecycleMetadata sidecar(SkillPack pack, double baseline, LifecycleState state) {
        return new LifecycleMetadata(SkillContentHash.of(pack), baseline, state, Instant.now().toString());
    }

    /** A failed, non-tier-block trace for the named skill, with the given step records. */
    static TraceRecord failedTrace(String runId, String skill, String input,
                                   TraceRecord.StepRecord... steps) {
        return trace(runId, skill, input, false, "run failed", steps);
    }

    /** A successful trace for the named skill. */
    static TraceRecord okTrace(String runId, String skill, String input,
                               TraceRecord.StepRecord... steps) {
        return trace(runId, skill, input, true, null, steps);
    }

    /** A trust-tier block trace (failed, no executed steps). */
    static TraceRecord tierBlockTrace(String runId, String skill, String input) {
        return trace(runId, skill, input, false, "Run blocked by trust tier: untrusted",
                new TraceRecord.StepRecord[0]);
    }

    private static TraceRecord trace(String runId, String skill, String input, boolean success,
                                     String error, TraceRecord.StepRecord... steps) {
        return new TraceRecord(runId, Instant.now(),
                new TraceRecord.SourceRef("folder:/tmp", null, null),
                new TraceRecord.PackRef(skill, "hash"),
                skill, input, "GOAP", "trusted",
                List.of(steps), success, error, success ? "ok" : null);
    }

    static TraceRecord.StepRecord step(String name, String status, String outputExcerpt) {
        return new TraceRecord.StepRecord(name, status, 10, outputExcerpt);
    }

    /** A pending candidate for approval-queue tests. */
    static PendingCandidate candidate(String id, String skill) {
        SkillPack pack = pack(skill, "s1");
        LifecycleMetadata proposed = sidecar(pack, 1.0, LifecycleState.ACTIVE);
        return new PendingCandidate(id, skill, pack, proposed, 1.0, 0.5, null,
                List.of(), "rewrote prompts of: s1");
    }
}
