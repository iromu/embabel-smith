package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.runtime.trace.TraceRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a candidate pack by rewriting the prompts of the steps implicated in the failures, using
 * the failure traces as evidence (the POC 9 pattern). Steps without failure evidence are left
 * unchanged, so the candidate differs from the current pack only in the failing steps' prompts
 * (plus the lifecycle metadata the loop adds later).
 */
public class Refiner {

    private final PromptRewriter rewriter;

    public Refiner(PromptRewriter rewriter) {
        this.rewriter = rewriter;
    }

    /**
     * The outcome of a refinement: the candidate pack and the names of the steps whose prompts were
     * rewritten (empty when nothing needed refining).
     */
    public record Refinement(SkillPack candidate, List<String> changedSteps) {

        public boolean changed() {
            return !changedSteps.isEmpty();
        }
    }

    public Refinement refine(SkillPack current, List<TraceRecord> failureTraces) {
        Set<String> failing = failingSteps(failureTraces);
        if (failing.isEmpty()) {
            return new Refinement(current, List.of());
        }
        List<ActionStepSpec> steps = new ArrayList<>(current.steps());
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            ActionStepSpec step = steps.get(i);
            if (!failing.contains(step.name())) {
                continue;
            }
            String evidence = evidenceFor(step.name(), failureTraces);
            String newPrompt = rewriter.rewrite(step.prompt(), evidence);
            steps.set(i, withPrompt(step, newPrompt));
            changed.add(step.name());
        }
        SkillPack candidate = new SkillPack(current.manifest(), current.goal(), steps, current.types());
        return new Refinement(candidate, List.copyOf(changed));
    }

    /** The names of the steps marked {@code failed} in the traces, in first-seen order. */
    public static Set<String> failingSteps(List<TraceRecord> failureTraces) {
        Set<String> out = new LinkedHashSet<>();
        for (TraceRecord record : failureTraces) {
            for (TraceRecord.StepRecord step : record.steps()) {
                if ("failed".equals(step.status())) {
                    out.add(step.name());
                }
            }
        }
        return out;
    }

    /**
     * The failure evidence for a step: the run's input, the step's output excerpt, and the run's
     * failure reason — the rewrite input the spec requires.
     */
    private static String evidenceFor(String stepName, List<TraceRecord> failureTraces) {
        List<String> lines = new ArrayList<>();
        for (TraceRecord record : failureTraces) {
            for (TraceRecord.StepRecord step : record.steps()) {
                if (!stepName.equals(step.name())) {
                    continue;
                }
                lines.add("run " + record.runId() + ": input '" + record.input() + "'");
                lines.add("step '" + stepName + "' output: " + step.outputExcerpt());
                lines.add("failure: " + record.error());
            }
        }
        return String.join("\n", lines);
    }

    private static ActionStepSpec withPrompt(ActionStepSpec step, String prompt) {
        return new ActionStepSpec(
                step.stepType(), step.name(), step.description(), step.inputTypeNames(),
                step.outputTypeName(), prompt, step.pre(), step.post(),
                step.cost(), step.value(), step.canRerun(), step.nullable(), step.toolGroups());
    }
}
