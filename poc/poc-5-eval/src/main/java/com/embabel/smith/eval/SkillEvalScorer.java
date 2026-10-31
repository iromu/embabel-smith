package com.embabel.smith.eval;

import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.eval.support.ResponseTimes;
import com.embabel.agent.eval.support.Score;
import com.embabel.agent.eval.support.SubjectiveScores;

import java.util.List;

/**
 * Deterministic, offline scoring of a goal-type skill's outputs, expressed in the eval module's own
 * {@link Score}/{@link FullScores} types so the composite is comparable to the conversational eval.
 *
 * <p>The conversational-only dimensions (subjective tone, response times) do not apply to a
 * single-shot goal run, so they are zeroed; the composite is the mean of the per-task assertion
 * scores.
 */
public class SkillEvalScorer {

    /**
     * Score one task: {@code 1.0} when the goal output is present and satisfies the acceptance
     * check, {@code 0.0} otherwise.
     */
    public Score scoreOne(String goalOutput, SkillEvalTask task) {
        boolean pass = goalOutput != null && !goalOutput.isBlank() && task.acceptance().test(goalOutput);
        return new Score(task.input(), pass ? 1.0 : 0.0);
    }

    /** Composite score across all tasks: the mean of the per-task assertion scores. */
    public FullScores composite(List<Score> assertionScores) {
        int failures = (int) assertionScores.stream().filter(s -> s.getScore() < 1.0).count();
        double total = assertionScores.isEmpty()
                ? 0.0
                : assertionScores.stream().mapToDouble(Score::getScore).average().orElse(0.0);
        return new FullScores(
                failures,
                new SubjectiveScores(0.0, List.of()),
                assertionScores,
                new ResponseTimes(0, 0, 0),
                total);
    }
}
