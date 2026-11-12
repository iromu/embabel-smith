package com.embabel.smith.refinement;

import com.embabel.agent.eval.support.FullScores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline checks for the refinement scorer: a passing output scores 1.0, a failing one
 * 0.0, and the composite is the mean.
 */
class RefinementScorerTest {

	private final RefinementScorer scorer = new RefinementScorer();

	private SpecEvalTask task(String input) {
		return new SpecEvalTask(input, "must contain Nimbus", out -> out != null && out.contains("Nimbus"));
	}

	@Test
	void scoresOneWhenTheAcceptanceCheckPasses() {
		assertThat(scorer.scoreOne("Brew with Nimbus", task("q")).getScore()).isEqualTo(1.0);
	}

	@Test
	void scoresZeroWhenTheAcceptanceCheckFails() {
		assertThat(scorer.scoreOne("Brew with joy", task("q")).getScore()).isEqualTo(0.0);
		assertThat(scorer.scoreOne(null, task("q")).getScore()).isEqualTo(0.0);
	}

	@Test
	void theCompositeIsTheMeanOfThePerTaskScores() {
		FullScores scores = scorer
			.composite(List.of(scorer.scoreOne("has Nimbus", task("a")), scorer.scoreOne("no brand", task("b"))));
		assertThat(scores.getTotalScore()).isEqualTo(0.5);
		assertThat(scores.getFailureCount()).isEqualTo(1);
	}

}
