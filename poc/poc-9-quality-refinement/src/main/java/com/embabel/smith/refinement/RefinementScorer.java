package com.embabel.smith.refinement;

import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.eval.support.ResponseTimes;
import com.embabel.agent.eval.support.Score;
import com.embabel.agent.eval.support.SubjectiveScores;

import java.util.List;

/**
 * Deterministic scoring of a goal-type spec's outputs, in the eval module's own {@link Score} /
 * {@link FullScores} types (same shape as POC 5). {@code 1.0} when the output is present and passes
 * the acceptance check, {@code 0.0} otherwise; the composite is the mean of the per-task scores.
 */
public class RefinementScorer {

    public Score scoreOne(String goalOutput, SpecEvalTask task) {
        boolean pass = goalOutput != null && !goalOutput.isBlank() && task.acceptance().test(goalOutput);
        return new Score(task.input(), pass ? 1.0 : 0.0);
    }

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
