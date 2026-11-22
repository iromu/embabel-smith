/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 4.4: run the inner loop to convergence over the free generations, and write a
 * per-generation record with zero model calls counted.
 *
 * <p>
 * The zero is the point, so it is measured rather than asserted. {@link ModelCallMeter}
 * is the one seam a paid call could pass through; each record carries the delta that
 * counter moved while its generation ran. The last test here spends a call through that
 * seam on purpose — if the number were a literal nobody could tell this suite from one
 * that silently reported zero for a loop that was quietly paying for every generation.
 *
 * @author Ivan Rodriguez
 */
class InnerLoopConvergenceTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@TempDir
	Path workDir;

	private List<PolicyGenerator.Variant> variants(int count) {
		return PolicyGenerator.variants(PolicyPrompt.frozenSeed(), 27L, count, PolicyGenerator.SEMANTIC_MUTATIONS);
	}

	private InnerLoop.Run run(List<PolicyGenerator.Variant> variants, int patience, ModelCallMeter meter) {
		return new InnerLoop(new PolicyRunner(InnerLoopFixtures.ENGINE), meter, InnerLoop.Listener.NONE).run(variants,
				InnerLoopFixtures.tuning(), InnerLoopFixtures.heldout(), patience, this.workDir);
	}

	@Test
	void everyGenerationRecordCountsModelCallsAndTheRunCountsNone() {
		ModelCallMeter meter = new ModelCallMeter();

		InnerLoop.Run run = run(variants(9), 4, meter);

		assertThat(run.records()).isNotEmpty();
		for (GenerationRecord record : run.records()) {
			assertThat(record.modelCalls()).as("generation " + record.index()).isZero();
		}
		assertThat(run.modelCalls()).isZero();
		assertThat(meter.count()).isZero();
	}

	@Test
	void theRunWritesOneRecordPerGenerationWithBothNumbersOnBothSplits() throws IOException {
		ModelCallMeter meter = new ModelCallMeter();

		InnerLoop.Run run = run(variants(9), 4, meter);

		List<String> lines = Files.readAllLines(run.recordFile(), StandardCharsets.UTF_8)
			.stream()
			.filter((line) -> !line.isBlank())
			.toList();
		assertThat(lines).hasSize(run.records().size());
		assertThat(run.recordFile().getFileName().toString()).isEqualTo(InnerLoop.RECORD_FILE);
		for (String line : lines) {
			JsonNode record = JSON.readTree(line);
			assertThat(record.has("variant")).isTrue();
			assertThat(record.has("parent")).isTrue();
			assertThat(record.has("operator")).isTrue();
			assertThat(record.get("model_calls").isNumber()).isTrue();
			JsonNode tuning = record.get("tuning");
			// Two numbers on the split it was fitted against, and two more on the split
			// it
			// was judged on. No key anywhere holds a blend of them.
			assertThat(tuning.get("precision").isNumber()).isTrue();
			assertThat(tuning.get("recall").isNumber()).isTrue();
			assertThat(record.get("heldout").get("precision").isNumber()).isTrue();
			assertThat(record.get("heldout").get("recall").isNumber()).isTrue();
		}
	}

	@Test
	void theLoopStopsWhenItsBestStopsMoving() {
		List<PolicyGenerator.Variant> variants = variants(12);

		InnerLoop.Run run = run(variants, 2, new ModelCallMeter());

		assertThat(run.records()).hasSizeLessThan(variants.size());
		assertThat(run.stopReason()).isEqualTo(InnerLoop.CONVERGED);
	}

	@Test
	void theSeedIsTheBestBecauseNothingBeatsItOnBothNumbers() {
		List<PolicyGenerator.Variant> variants = variants(9);

		InnerLoop.Run run = run(variants, 4, new ModelCallMeter());

		// The seed is the product's own rule, so on this corpus it scores 1.0 and 1.0 and
		// every
		// mutation can only trade one for the other. Selection is lexicographic, so a tie
		// keeps
		// the seed, and the archive still holds every variant that lost.
		assertThat(run.bestVariantId()).isEqualTo(variants.get(0).variantId());
		GenerationRecord seed = run.records().get(0);
		assertThat(seed.tuning().precision()).isEqualTo(1.0);
		assertThat(seed.tuning().recall()).isEqualTo(1.0);
		assertThat(run.records()
			.stream()
			.filter((record) -> GenerationRecord.SCORED.equals(record.status()) && record.index() > 0)
			.toList()).isNotEmpty().allSatisfy((lost) -> assertThat(lost.parentId()).isEqualTo(seed.variantId()));
	}

	@Test
	void theCountIsReadFromTheMeterRatherThanTypedIn() {
		ModelCallMeter meter = new ModelCallMeter();
		AtomicInteger paidFor = new AtomicInteger();
		InnerLoop.Listener paysOnGenerationOne = new InnerLoop.Listener() {
			@Override
			public void beforeGeneration(PolicyGenerator.Variant variant) {
				if (variant.index() == 1) {
					meter.counted(() -> paidFor.incrementAndGet());
				}
			}
		};

		InnerLoop.Run run = new InnerLoop(new PolicyRunner(InnerLoopFixtures.ENGINE), meter, paysOnGenerationOne)
			.run(variants(4), InnerLoopFixtures.tuning(), InnerLoopFixtures.heldout(), 4, this.workDir);

		assertThat(paidFor.get()).isEqualTo(1);
		assertThat(run.records().get(1).modelCalls()).isEqualTo(1);
		assertThat(run.records().get(0).modelCalls()).isZero();
		assertThat(run.records().get(2).modelCalls()).isZero();
		assertThat(run.modelCalls()).isEqualTo(1);
	}

}
