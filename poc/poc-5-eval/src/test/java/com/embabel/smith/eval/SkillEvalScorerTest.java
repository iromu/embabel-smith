package com.embabel.smith.eval;

import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.eval.support.Score;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the goal-type scoring (task 6.1's verifiable core): per-task scoring and the
 * composite, expressed in the eval module's own {@link Score}/{@link FullScores} types.
 */
class SkillEvalScorerTest {

    private final SkillEvalScorer scorer = new SkillEvalScorer();

    @Test
    void scoresOnePassingTaskAsOne() {
        SkillEvalTask task = new SkillEvalTask("Explain coffee", "mentions coffee", out -> out.contains("coffee"));
        Score score = scorer.scoreOne("Here is how a coffee machine brews.", task);
        assertThat(score.getScore()).isEqualTo(1.0);
    }

    @Test
    void scoresOneFailingTaskAsZero() {
        SkillEvalTask task = new SkillEvalTask("Explain coffee", "mentions coffee", out -> out.contains("coffee"));
        assertThat(scorer.scoreOne("Tea is great.", task).getScore()).isEqualTo(0.0);
        assertThat(scorer.scoreOne("", task).getScore()).isEqualTo(0.0);
        assertThat(scorer.scoreOne(null, task).getScore()).isEqualTo(0.0);
    }

    @Test
    void compositeIsTheMeanOfAssertionScores() {
        List<Score> scores = List.of(new Score("a", 1.0), new Score("b", 0.0), new Score("c", 1.0));
        FullScores full = scorer.composite(scores);
        assertThat(full.getTotalScore()).isEqualTo(2.0 / 3.0);
        assertThat(full.getFailureCount()).isEqualTo(1);
        assertThat(full.getAssertionScores()).hasSize(3);
    }
}
