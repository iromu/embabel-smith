/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for the recommendation: the process wins only when it is both sound and
 * better, and every other outcome — invalid, tied, absent — keeps the single-agent spec.
 *
 * @author Ivan Rodriguez
 */
class CandidateRecommenderTest {

	private final Candidate single = Candidate.singleAgent("trading", 0.5);

	@Test
	void aValidHigherScoringProcessIsRecommended() {
		Recommendation recommendation = Recommendation.choose(this.single,
				Candidate.multiAgent("trading-process", true, 0.75), null);

		assertThat(recommendation.shape()).isEqualTo(CandidateShape.MULTI_AGENT);
		assertThat(recommendation.chosen().name()).isEqualTo("trading-process");
		assertThat(recommendation.score()).isEqualTo(0.75);
		assertThat(recommendation.reason()).contains("out-scored").contains("0.750").contains("0.500");
	}

	@Test
	void anInvalidProcessFallsBackToTheSingleAgentSpec() {
		Recommendation recommendation = Recommendation.choose(this.single,
				Candidate.multiAgent("trading-process", false, null), "the process carried a cycle");

		assertThat(recommendation.shape()).isEqualTo(CandidateShape.SINGLE_AGENT);
		assertThat(recommendation.reason()).contains("did not validate").contains("carried a cycle");
	}

	@Test
	void aTieRecommendsTheSingleAgentSpec() {
		Recommendation recommendation = Recommendation.choose(this.single,
				Candidate.multiAgent("trading-process", true, 0.5), null);

		assertThat(recommendation.shape()).isEqualTo(CandidateShape.SINGLE_AGENT);
		assertThat(recommendation.reason()).contains("tie-break");
	}

	@Test
	void aLowerScoringProcessLosesToTheSingleAgentSpec() {
		Recommendation recommendation = Recommendation.choose(Candidate.singleAgent("trading", 1.0),
				Candidate.multiAgent("trading-process", true, 0.25), null);

		assertThat(recommendation.shape()).isEqualTo(CandidateShape.SINGLE_AGENT);
		assertThat(recommendation.runnerUp().shape()).isEqualTo(CandidateShape.MULTI_AGENT);
	}

	@Test
	void noProcessAtAllLeavesTheSingleAgentSpec() {
		Recommendation recommendation = Recommendation.choose(this.single, null, "the model returned no JSON");

		assertThat(recommendation.shape()).isEqualTo(CandidateShape.SINGLE_AGENT);
		assertThat(recommendation.runnerUp()).isNull();
		assertThat(recommendation.reason()).contains("no multi-agent candidate").contains("returned no JSON");
	}

}
