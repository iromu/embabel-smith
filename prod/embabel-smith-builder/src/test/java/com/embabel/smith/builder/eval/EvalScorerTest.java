package com.embabel.smith.builder.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the eval scorer: a blank {@code expected} criterion means "the run
 * produced output" (the smoke default), so a blank or missing output does not pass; a
 * non-blank criterion is a case-insensitive substring match.
 */
class EvalScorerTest {

	private final EvalTask smoke = new EvalTask("t", "in", "");

	@Test
	void blankExpectedPassesOnlyOnNonBlankOutput() {
		assertThat(EvalScorer.score(List.of(smoke), List.of("some output"))).isEqualTo(1.0);
		assertThat(EvalScorer.score(List.of(smoke), List.of("   "))).isZero();
		assertThat(EvalScorer.score(List.of(smoke), List.of(""))).isZero();
		assertThat(EvalScorer.score(List.of(smoke), List.of())).isZero(); // no output at
																			// all
	}

	@Test
	void blankExpectedNamesTheFailingTask() {
		assertThat(EvalScorer.failingTasks(List.of(smoke), List.of("   "))).containsExactly("t");
		assertThat(EvalScorer.failingTasks(List.of(smoke), List.of("some output"))).isEmpty();
	}

	@Test
	void nonBlankExpectedIsACaseInsensitiveSubstringMatch() {
		EvalTask task = new EvalTask("t", "in", "Hello");
		assertThat(EvalScorer.score(List.of(task), List.of("say hello there"))).isEqualTo(1.0);
		assertThat(EvalScorer.score(List.of(task), List.of("goodbye"))).isZero();
		assertThat(EvalScorer.score(List.of(task), List.of())).isZero(); // no output at
																			// all
	}

}
