/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.improve;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.runtime.trace.TraceRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefinerTest {

	@Test
	void onlyFailingStepPromptChanges() {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2", "s3");
		List<TraceRecord> failures = List
			.of(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s1", "completed", "ok"),
					TestFixtures.step("s2", "failed", "bad output"), TestFixtures.step("s3", "completed", "ok")));

		Refiner refiner = new Refiner((current, feedback) -> current + " [revised]");
		Refiner.Refinement refinement = refiner.refine(pack, failures);

		assertThat(refinement.changed()).isTrue();
		assertThat(refinement.changedSteps()).containsExactly("s2");
		assertThat(refinement.candidate().step("s2").prompt()).isNotEqualTo(pack.step("s2").prompt());
		assertThat(refinement.candidate().step("s2").prompt()).contains("[revised]");
		// The steps without failure evidence are left unchanged.
		assertThat(refinement.candidate().step("s1").prompt()).isEqualTo(pack.step("s1").prompt());
		assertThat(refinement.candidate().step("s3").prompt()).isEqualTo(pack.step("s3").prompt());
	}

	@Test
	void evidenceIsInRewriteInput() {
		AgentSpec pack = TestFixtures.pack("extract", "s2");
		List<TraceRecord> failures = List.of(TestFixtures.failedTrace("r1", "extract", "the input",
				TestFixtures.step("s2", "failed", "the output excerpt")));

		String[] captured = new String[1];
		Refiner refiner = new Refiner((current, feedback) -> {
			captured[0] = feedback;
			return current + " [revised]";
		});
		refiner.refine(pack, failures);

		assertThat(captured[0]).contains("the input");
		assertThat(captured[0]).contains("the output excerpt");
		assertThat(captured[0]).contains("run failed"); // the failure reason
	}

	@Test
	void noFailureEvidenceLeavesPackUnchanged() {
		AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
		// A failed run with no executed steps (e.g. a model abort) gives no step-level
		// evidence.
		List<TraceRecord> failures = List.of(TestFixtures.failedTrace("r1", "extract", "input"));

		Refiner refiner = new Refiner((current, feedback) -> current + " [revised]");
		Refiner.Refinement refinement = refiner.refine(pack, failures);

		assertThat(refinement.changed()).isFalse();
		assertThat(refinement.changedSteps()).isEmpty();
		assertThat(refinement.candidate()).isSameAs(pack);
	}

	// ---- The candidate's version (change: publish-keeps-versions, task 3.2a) ----

	private static final List<TraceRecord> S2_FAILED = List
		.of(TestFixtures.failedTrace("r1", "extract", "input", TestFixtures.step("s2", "failed", "bad output")));

	/**
	 * The pack under test, at a chosen version and with the optional manifest fields set.
	 */
	private static AgentSpec packAtVersion(String version) {
		AgentSpec base = TestFixtures.pack("extract", "s2");
		return new AgentSpec(new AgentSpecManifest("extract", version, "GOAP", "gpt-4o"), base.goal(), base.steps(),
				base.types());
	}

	private static Refiner.Refinement refine(AgentSpec pack) {
		return new Refiner((current, feedback) -> current + " [revised]").refine(pack, S2_FAILED);
	}

	@Test
	void candidateIsOnePatchAboveThePackItImproves() {
		AgentSpec pack = packAtVersion("1.2.3");

		Refiner.Refinement refinement = refine(pack);

		assertThat(refinement.candidate().version()).isEqualTo("1.2.4");
		// The baseline object is untouched: the loop reads it again as the next pass's
		// baseline.
		assertThat(pack.version()).isEqualTo("1.2.3");
	}

	@Test
	void candidateCarriesTheManifestFieldsTheBumpDidNotChange() {
		AgentSpec pack = packAtVersion("1.2.3");

		AgentSpecManifest candidate = refine(pack).candidate().manifest();

		// The manifest is a record with no withVersion, so a replacement that forgot
		// these
		// would drop them silently rather than fail.
		assertThat(candidate.name()).isEqualTo("extract");
		assertThat(candidate.defaultPlanner()).isEqualTo("GOAP");
		assertThat(candidate.defaultModel()).isEqualTo("gpt-4o");
	}

	@Test
	void candidateOfAPackDeclaringNoVersionStartsAboveTheStoredDefault() {
		assertThat(refine(packAtVersion(null)).candidate().version()).isEqualTo("1.0.1");
		assertThat(refine(packAtVersion("  ")).candidate().version()).isEqualTo("1.0.1");
	}

	@Test
	void candidateOfANonNumericVersionKeepsTheVersionItDeclares() {
		// Inventing a numeric segment would rewrite a version the artifact states; the
		// outcome is then the store's same-version update, which is the honest answer.
		assertThat(refine(packAtVersion("1.0.0-rc1")).candidate().version()).isEqualTo("1.0.0-rc1");
		assertThat(refine(packAtVersion("v3")).candidate().version()).isEqualTo("v3");
	}

}
