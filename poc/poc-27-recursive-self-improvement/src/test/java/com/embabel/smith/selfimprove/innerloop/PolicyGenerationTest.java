/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.skillscripts.GraalJsSkillScriptExecutionEngine;
import com.embabel.smith.studio.improve.Detector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 4.2: detection policies generated from the frozen seed prompt, by deterministic
 * mutation, and what happens when a generation emits code that does not compile.
 *
 * <p>
 * Two properties hold this together. Determinism, because the inner loop's whole claim is
 * that a generation differs from its parent by the one mutation named on it — if the same
 * seed produced different bytes, a score movement could not be attributed to anything.
 * And survival, because a generator that emits broken code is normal, and a loop that
 * dies on it is reporting the wrong failure.
 *
 * @author Ivan Rodriguez
 */
class PolicyGenerationTest {

	@TempDir
	Path workDir;

	private List<PolicyGenerator.Variant> variants(long seed, int count, List<PolicyGenerator.MutationOperator> pool) {
		return PolicyGenerator.variants(PolicyPrompt.frozenSeed(), seed, count, pool);
	}

	private List<PolicyGenerator.MutationOperator> everyOperator() {
		return List.of(PolicyGenerator.MutationOperator.values());
	}

	@Test
	void theSameSeedGeneratesTheSameVariants() {
		List<PolicyGenerator.Variant> first = variants(27L, 8, everyOperator());
		List<PolicyGenerator.Variant> second = variants(27L, 8, everyOperator());

		assertThat(first.stream().map(PolicyGenerator.Variant::variantId).toList())
			.isEqualTo(second.stream().map(PolicyGenerator.Variant::variantId).toList());
		assertThat(first.stream().map((variant) -> variant.script().source()).toList())
			.isEqualTo(second.stream().map((variant) -> variant.script().source()).toList());
		// A different seed has to actually move the draw, or "deterministic" would be
		// satisfied by a generator that ignores its seed.
		assertThat(variants(28L, 8, everyOperator()).stream().map(PolicyGenerator.Variant::variantId).toList())
			.isNotEqualTo(first.stream().map(PolicyGenerator.Variant::variantId).toList());
	}

	@Test
	void everyGeneratedPolicyParsesAndRunsUnderTheEngineLimits() {
		List<TraceCase> tuning = InnerLoopFixtures.tuning();
		for (PolicyGenerator.Variant variant : variants(27L, 6, PolicyGenerator.SEMANTIC_MUTATIONS)) {
			if (variant.operator() == PolicyGenerator.MutationOperator.MALFORMED_EMIT) {
				continue;
			}
			PolicyRun run = InnerLoopFixtures.run(variant.script(), tuning, this.workDir);
			// A breach of either limit surfaces as a Failure whose message names the
			// limit
			// (POC 26 pins both wordings), so RAN is the proof that the script stayed
			// inside
			// them. The engine here is the default one, with the documented defaults.
			assertThat(run.ran()).as(variant.variantId() + ": " + run.diagnostic()).isTrue();
			assertThat(run.durationMillis()).isLessThan(GraalJsSkillScriptExecutionEngine.DEFAULT_TIMEOUT_MILLIS);
		}
	}

	@Test
	void theSeedCompilesToTheProductOwnBlockPrefixes() {
		DetectionPolicy compiled = PolicyCompiler.compile(PolicyPrompt.frozenSeed());

		// The compiled policy excludes by the product's own strings, matched as prefixes.
		// If
		// someone edits the seed prompt to paraphrase one of these, the policy stops
		// agreeing
		// with Detector for a reason no mutation operator accounts for.
		assertThat(compiled.source()).contains("startsWith(" + quote(Detector.TIER_BLOCK_PREFIX) + ")")
			.contains("startsWith(" + quote(Detector.LIFECYCLE_BLOCK_PREFIX) + ")");
		assertThat(compiled.source()).contains("reasons.push(\"failed_step\")")
			.contains("reasons.push(\"content_drift\")")
			.contains("reasons.push(\"below_baseline\")");
	}

	@Test
	void aSyntacticallyInvalidGenerationIsRecordedAndTheLoopCarriesOn() {
		List<PolicyGenerator.Variant> variants = List.of(
				PolicyGenerator.emit(PolicyPrompt.frozenSeed(), PolicyPrompt.frozenSeed().name(), 0,
						PolicyGenerator.MutationOperator.SEED),
				PolicyGenerator.emit(PolicyPrompt.frozenSeed(), PolicyPrompt.frozenSeed().name(), 1,
						PolicyGenerator.MutationOperator.MALFORMED_EMIT),
				PolicyGenerator.emit(PolicyPrompt.frozenSeed(), PolicyPrompt.frozenSeed().name(), 2,
						PolicyGenerator.MutationOperator.DROP_TRIGGER));
		ModelCallMeter meter = new ModelCallMeter();

		InnerLoop.Run run = new InnerLoop(new PolicyRunner(InnerLoopFixtures.ENGINE), meter, InnerLoop.Listener.NONE)
			.run(variants, InnerLoopFixtures.tuning(), InnerLoopFixtures.heldout(), 5, this.workDir);

		assertThat(run.records()).hasSize(3);
		assertThat(run.records().get(1).status()).isEqualTo(GenerationRecord.FAILED);
		assertThat(run.records().get(1).diagnostic()).isNotBlank();
		// The broken generation cost the run nothing but itself: the one after it scored.
		assertThat(run.records().get(2).status()).isEqualTo(GenerationRecord.SCORED);
		assertThat(run.records().get(2).heldout()).isNotNull();
		assertThat(run.modelCalls()).isZero();
	}

	private static String quote(String text) {
		return "\"" + text + "\"";
	}

}
