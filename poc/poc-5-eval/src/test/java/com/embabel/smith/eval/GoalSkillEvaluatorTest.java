package com.embabel.smith.eval;

import com.embabel.agent.eval.support.FullScores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline test of the full evaluate → score → composite flow (task 6.1) using a fake {@link
 * SkillInvoker}, so no live model is needed. The endpoint-gated half (the real
 * {@link AgentInvocationSkillInvoker}) is exercised only at runtime.
 */
class GoalSkillEvaluatorTest {

    private final GoalSkillEvaluator evaluator = new GoalSkillEvaluator();

    @Test
    void evaluatesAllTasksAndCompositesOffline() {
        // A deterministic stand-in for the skill: "answers" based on the input. No LLM, no endpoint.
        SkillInvoker fake = input -> input.contains("coffee") ? "How a coffee machine brews." : "A generic answer.";
        List<SkillEvalTask> tasks = List.of(
                new SkillEvalTask("Explain coffee", "mentions coffee", out -> out.contains("coffee")),
                new SkillEvalTask("Explain tea", "mentions coffee", out -> out.contains("coffee")));

        FullScores scores = evaluator.evaluate(fake, tasks);

        assertThat(scores.getTotalScore()).isEqualTo(0.5);
        assertThat(scores.getFailureCount()).isEqualTo(1);
        assertThat(scores.getAssertionScores()).hasSize(2);
    }
}
