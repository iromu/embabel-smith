/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 4.5: a policy that reaches outside its sandbox is recorded as a rejected
 * generation naming the fixed artifact it tried to reach, and the denial is what caused
 * the rejection.
 *
 * <p>
 * The reach is real rather than a label: the probes are the {@code Java.type} host-class
 * lookup and a {@code readFile} of the held-out split, both of which POC 26's hardening
 * refuses, and the first test here runs them straight through the engine so the denial
 * text is on the record before any classification happens. That matters because the loop
 * derives the artifact from the engine's words, not from the operator that emitted the
 * probe — a mapping keyed on the operator name would still "work" after the hardening was
 * removed, which is precisely the failure this task is about.
 *
 * <p>
 * The boundary itself is cited, not restated: {@code evidence/MUTABILITY.md} is what says
 * these two surfaces are fixed, so the last test reads the file the record points at and
 * checks it uses the same names.
 *
 * @author Ivan Rodriguez
 */
class SandboxBreachRejectionTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final String HOST_CLASS = "script-engine-limits";

	private static final String CORPUS = "trace-corpus";

	@TempDir
	Path workDir;

	private PolicyGenerator.Variant variant(int index, PolicyGenerator.MutationOperator operator) {
		return PolicyGenerator.emit(PolicyPrompt.frozenSeed(), PolicyPrompt.frozenSeed().name(), index, operator);
	}

	private InnerLoop.Run loop(PolicyGenerator.Variant... variants) {
		return new InnerLoop(new PolicyRunner(InnerLoopFixtures.ENGINE), new ModelCallMeter(), InnerLoop.Listener.NONE)
			.run(List.of(variants), InnerLoopFixtures.tuning(), InnerLoopFixtures.heldout(), 5, this.workDir);
	}

	private PolicyRun reach(PolicyGenerator.MutationOperator operator) {
		return InnerLoopFixtures.run(variant(1, operator).script(), InnerLoopFixtures.heldout(), this.workDir);
	}

	@Test
	void theHardeningDeniesBothReachesAndTheArtifactIsReadOutOfTheDenial() {
		PolicyRun host = reach(PolicyGenerator.MutationOperator.HOST_CLASS_PROBE);
		assertThat(host.ran()).as("the host class lookup must not run").isFalse();
		assertThat(host.diagnostic()).contains("not allowed");

		PolicyRun corpus = reach(PolicyGenerator.MutationOperator.CORPUS_READ_PROBE);
		assertThat(corpus.ran()).as("the corpus read must not run").isFalse();
		assertThat(corpus.diagnostic()).contains("was not staged").contains(PolicyGenerator.HELDOUT_FILE);

		// The classification is a function of the denial alone, so the record's reason is
		// the
		// engine's refusal and not a guess from the operator's name.
		assertThat(InnerLoop.fixedArtifact(host.diagnostic())).isEqualTo(HOST_CLASS);
		assertThat(InnerLoop.fixedArtifact(corpus.diagnostic())).isEqualTo(CORPUS);
		// And a run that merely failed to answer is not a breach of anything.
		assertThat(InnerLoop.fixedArtifact("policy printed no verdicts at all")).isNull();
	}

	@Test
	void aGenerationThatReachesForAHostClassIsRejectedAndNamesTheArtifact() {
		InnerLoop.Run run = loop(variant(0, PolicyGenerator.MutationOperator.SEED),
				variant(1, PolicyGenerator.MutationOperator.HOST_CLASS_PROBE));

		assertThat(run.records()).hasSize(2);
		GenerationRecord breach = run.records().get(1);
		assertThat(breach.status()).isEqualTo(GenerationRecord.REJECTED);
		assertThat(breach.rejectedFixedArtifact()).isEqualTo(HOST_CLASS);
		assertThat(breach.diagnostic()).contains("not allowed");
		// Named, recorded, and scored as nothing: a score next to the rejection would be
		// the
		// accepted-and-scored outcome the spec rules out.
		assertThat(breach.tuning()).isNull();
		assertThat(breach.heldout()).isNull();
		assertThat(breach.modelCalls()).isZero();
		assertThat(run.stopReason()).isEqualTo(InnerLoop.STOPPED_AT_REACH);
	}

	@Test
	void aGenerationThatReadsTheSplitThatGradesItNamesTheCorpus() {
		InnerLoop.Run run = loop(variant(0, PolicyGenerator.MutationOperator.SEED),
				variant(1, PolicyGenerator.MutationOperator.CORPUS_READ_PROBE));

		GenerationRecord breach = run.records().get(1);
		assertThat(breach.status()).isEqualTo(GenerationRecord.REJECTED);
		assertThat(breach.rejectedFixedArtifact()).isEqualTo(CORPUS);
		assertThat(breach.diagnostic()).contains(PolicyGenerator.HELDOUT_FILE);
		assertThat(breach.tuning()).isNull();
	}

	@Test
	void theRunStopsAtTheBreachInsteadOfScoringPastIt() {
		InnerLoop.Run run = loop(variant(0, PolicyGenerator.MutationOperator.HOST_CLASS_PROBE),
				variant(1, PolicyGenerator.MutationOperator.SEED), variant(2, PolicyGenerator.MutationOperator.SEED));

		// Continuing would be continuing past a breach of the boundary the whole claim is
		// relative
		// to, so the two generations after it never ran and nothing was selected.
		assertThat(run.records()).hasSize(1);
		assertThat(run.records().get(0).status()).isEqualTo(GenerationRecord.REJECTED);
		assertThat(run.bestVariantId()).isNull();
		assertThat(run.stopReason()).isEqualTo(InnerLoop.STOPPED_AT_REACH);
	}

	@Test
	void aGenerationThatMerelyBreaksFailsRatherThanGettingRejected() {
		InnerLoop.Run run = loop(variant(0, PolicyGenerator.MutationOperator.SEED),
				variant(1, PolicyGenerator.MutationOperator.MALFORMED_EMIT),
				variant(2, PolicyGenerator.MutationOperator.DROP_TRIGGER));

		// The control: an unparseable generation and a semantic mutation both go through
		// the same
		// classifier and neither is a breach. Without this the rejection test would be
		// satisfied by
		// a loop that rejects every generation that does not score.
		assertThat(run.records().get(1).status()).isEqualTo(GenerationRecord.FAILED);
		assertThat(run.records().get(1).rejectedFixedArtifact()).isNull();
		assertThat(run.records().get(2).status()).isEqualTo(GenerationRecord.SCORED);
		assertThat(run.stopReason()).isEqualTo(InnerLoop.BUDGET_EXHAUSTED);
	}

	@Test
	void theWrittenRecordCitesTheBoundaryItBreachedAndTheCitationResolves() throws IOException {
		InnerLoop.Run run = loop(variant(0, PolicyGenerator.MutationOperator.SEED),
				variant(1, PolicyGenerator.MutationOperator.HOST_CLASS_PROBE));

		List<String> lines = Files.readAllLines(run.recordFile(), StandardCharsets.UTF_8)
			.stream()
			.filter((line) -> !line.isBlank())
			.toList();
		assertThat(lines).hasSize(2);
		JsonNode breach = JSON.readTree(lines.get(1));
		assertThat(breach.get("status").asText()).isEqualTo(GenerationRecord.REJECTED);
		assertThat(breach.get("tuning").isNull()).isTrue();
		assertThat(breach.get("heldout").isNull()).isTrue();
		assertThat(breach.get("rejected_fixed_artifact").asText()).isEqualTo(HOST_CLASS);
		assertThat(breach.get("boundary").asText()).isEqualTo(GenerationRecord.BOUNDARY);
		assertThat(breach.get("rejection_reason").asText()).contains(HOST_CLASS).contains("not allowed");

		// The citation is not a dangling path: the file it names exists, and it is the
		// document that
		// declares both artifacts by the names the record uses.
		String boundary = boundaryText();
		assertThat(boundary).contains(HOST_CLASS).contains(CORPUS);
	}

	private String boundaryText() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/" + GenerationRecord.BOUNDARY)) {
			assertThat(in).as("the record cites " + GenerationRecord.BOUNDARY + ", which must exist").isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
