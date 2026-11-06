package com.embabel.smith.builder.eval;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * The bounded eval → refine loop (design D1, extracted from POC 5 + POC 9). Each round it runs the
 * built pack against the eval tasks, and — while the score is below the gate and the iteration
 * bound has not been reached — rewrites the terminal action's prompt from the failures,
 * re-validates it, and tries again. It never corrupts the pack: a rewrite that fails validation is
 * discarded.
 */
public class RefinementLoop {

    private final InvokerFactory invokerFactory;
    private final PromptRewriter rewriter;
    private final List<EvalTask> tasks;
    private final double scoreGate;
    private final int maxRefine;

    private final SkillPackValidator validator = new SkillPackValidator();

    public RefinementLoop(InvokerFactory invokerFactory, PromptRewriter rewriter,
                          List<EvalTask> tasks, double scoreGate, int maxRefine) {
        this.invokerFactory = invokerFactory;
        this.rewriter = rewriter;
        this.tasks = tasks == null ? List.of() : List.copyOf(tasks);
        this.scoreGate = scoreGate;
        this.maxRefine = Math.max(0, maxRefine);
    }

    public RefinementOutcome refine(SkillPack initial) {
        SkillPack current = initial;
        double score = 0.0;
        List<String> failures = List.of();
        int refinements = 0;
        for (int round = 0; round <= maxRefine; round++) {
            SkillInvoker invoker = invokerFactory.invokerFor(current);
            List<String> outputs = new ArrayList<>();
            for (EvalTask task : tasks) {
                outputs.add(invoker.invoke(task.input()));
            }
            failures = EvalScorer.failingTasks(tasks, outputs);
            score = EvalScorer.score(tasks, outputs);
            if (score >= scoreGate) {
                break; // gate met
            }
            if (round == maxRefine) {
                break; // iteration bound reached
            }
            current = rewriteTerminalPrompt(current, failures);
            refinements++;
        }
        return new RefinementOutcome(current, score, refinements, failures, score >= scoreGate);
    }

    /**
     * Rewrites the terminal action's prompt (the one producing the goal output) from the failures,
     * re-validates the resulting pack, and returns it — or the original pack when the rewrite does
     * not validate, so the loop never corrupts a good draft.
     */
    private SkillPack rewriteTerminalPrompt(SkillPack pack, List<String> failures) {
        ActionStepSpec terminal = terminalAction(pack);
        if (terminal == null) {
            return pack;
        }
        String newPrompt = rewriter.rewrite(terminal.prompt(), "Eval failures: " + failures);
        ActionStepSpec revised = new ActionStepSpec(
                terminal.stepType(), terminal.name(), terminal.description(), terminal.inputTypeNames(),
                terminal.outputTypeName(), newPrompt, terminal.pre(), terminal.post(),
                terminal.cost(), terminal.value(), terminal.canRerun(), terminal.nullable(), terminal.toolGroups());
        List<ActionStepSpec> steps = pack.steps().stream()
                .map(s -> s.name().equals(terminal.name()) ? revised : s)
                .toList();
        SkillPack candidate = new SkillPack(pack.manifest(), pack.goal(), steps, pack.types());
        return validator.validate(candidate).isValid() ? candidate : pack;
    }

    /** The action whose output is the goal output (the last such step); the last step as a fallback. */
    private ActionStepSpec terminalAction(SkillPack pack) {
        if (pack.steps().isEmpty()) {
            return null;
        }
        String goalOutput = pack.goal() == null ? null : pack.goal().outputTypeName();
        return pack.steps().stream()
                .filter(s -> goalOutput != null && goalOutput.equals(s.outputTypeName()))
                .reduce((a, b) -> b)
                .orElseGet(() -> pack.steps().get(pack.steps().size() - 1));
    }
}
