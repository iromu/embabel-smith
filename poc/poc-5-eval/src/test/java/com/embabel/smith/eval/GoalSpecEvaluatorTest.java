package com.embabel.smith.eval;

import com.embabel.agent.eval.support.FullScores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline test of the full evaluate → score → composite flow (task 6.1) using a fake {@link
 * SpecInvoker}, so no live model is needed. The endpoint-gated half (the real
 * {@link AgentInvocationSpecInvoker}) is exercised only at runtime.
 */
class GoalSpecEvaluatorTest {

    private final GoalSpecEvaluator evaluator = new GoalSpecEvaluator();

    @Test
    void evaluatesAllTasksAndCompositesOffline() {
        // A deterministic stand-in for the spec: "answers" based on the input. No LLM, no endpoint.
        SpecInvoker fake = input -> input.contains("coffee") ? "How a coffee machine brews." : "A generic answer.";
        List<SpecEvalTask> tasks = List.of(
                new SpecEvalTask("Explain coffee", "mentions coffee", out -> out.contains("coffee")),
                new SpecEvalTask("Explain tea", "mentions coffee", out -> out.contains("coffee")));

        FullScores scores = evaluator.evaluate(fake, tasks);

        assertThat(scores.getTotalScore()).isEqualTo(0.5);
        assertThat(scores.getFailureCount()).isEqualTo(1);
        assertThat(scores.getAssertionScores()).hasSize(2);
    }
}
