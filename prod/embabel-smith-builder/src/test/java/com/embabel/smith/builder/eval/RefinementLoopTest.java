package com.embabel.smith.builder.eval;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the bounded refinement loop (task 2.3): a passing eval retains the prompts, a
 * failing eval refines them, the iteration bound stops the loop, and a below-gate score blocks
 * publication. The invoker and rewriter are fakes keyed off a {@code FAIL_MARKER} in the terminal
 * action's prompt.
 */
class RefinementLoopTest {

    private static final String USER_INPUT = SampleSkillPacks.USER_INPUT;
    private static final String BACKGROUND_MESSAGE = SampleSkillPacks.BACKGROUND_MESSAGE;

    private static SkillPack packWithPrompt(String prompt) {
        ActionStepSpec say = ActionStepSpec.action("say", "Say it",
                List.of(USER_INPUT), BACKGROUND_MESSAGE, prompt);
        GoalStepSpec goal = GoalStepSpec.exportedGoal("done", "A single-step skill", BACKGROUND_MESSAGE);
        return new SkillPack(new SkillPackManifest("test-skill", "1.0.0"), goal, List.of(say), List.of());
    }

    /** Invoker that fails while the terminal prompt carries the marker, otherwise passes. */
    private static InvokerFactory markerInvoker() {
        return pack -> input -> pack.steps().get(0).prompt().contains("FAIL_MARKER")
                ? "still broken"
                : "correct output";
    }

    private static PromptRewriter markerRemover() {
        return (current, feedback) -> current.replace("FAIL_MARKER", "fixed");
    }

    @Test
    void passingEvalRetainsThePrompts() {
        RefinementLoop loop = new RefinementLoop(markerInvoker(), markerRemover(),
                List.of(new EvalTask("t", "in", "correct")), 1.0, 3);

        RefinementOutcome outcome = loop.refine(packWithPrompt("do the thing"));

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.refinementCount()).isZero();
        assertThat(outcome.pack().steps().get(0).prompt()).isEqualTo("do the thing");
    }

    @Test
    void failingEvalRefinesThePrompt() {
        RefinementLoop loop = new RefinementLoop(markerInvoker(), markerRemover(),
                List.of(new EvalTask("t", "in", "correct")), 1.0, 3);

        RefinementOutcome outcome = loop.refine(packWithPrompt("FAIL_MARKER do the thing"));

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.refinementCount()).isEqualTo(1);
        assertThat(outcome.pack().steps().get(0).prompt()).isEqualTo("fixed do the thing");
    }

    @Test
    void theBoundStopsTheLoop() {
        // The invoker always fails, so the loop rewrites until the bound is reached.
        InvokerFactory alwaysFails = pack -> input -> "still broken";
        RefinementLoop loop = new RefinementLoop(alwaysFails, markerRemover(),
                List.of(new EvalTask("t", "in", "correct")), 1.0, 2);

        RefinementOutcome outcome = loop.refine(packWithPrompt("FAIL_MARKER do the thing"));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.refinementCount()).isEqualTo(2);
        assertThat(outcome.score()).isZero();
    }

    @Test
    void aBelowGateScoreDoesNotPass() {
        // Two tasks; the invoker passes only one, so the score is 0.5, below the gate of 1.0.
        InvokerFactory halfPass = pack -> input -> "correct output";
        PromptRewriter noOp = (current, feedback) -> current;
        RefinementLoop loop = new RefinementLoop(halfPass, noOp,
                List.of(new EvalTask("pass", "A", "correct"), new EvalTask("fail", "B", "absent")),
                1.0, 2);

        RefinementOutcome outcome = loop.refine(packWithPrompt("do the thing"));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.score()).isEqualTo(0.5);
        assertThat(outcome.failingTasks()).containsExactly("fail");
    }
}
