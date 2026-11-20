/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Task 4.3: score a generated policy against the labelled corpus and report precision and
 * recall separately on the held-out split.
 *
 * <p>
 * The spec's clause is that a mixed policy yields two numbers rather than one blended
 * score, and the reason is in the corpus README: a loop that wants to believe failures
 * exist buys recall with precision, and a single F-shaped number hides exactly that
 * trade. So this suite scores a policy that is wrong in both directions and checks the
 * two numbers come out different, and checks the score type has no third number to blend
 * into.
 *
 * @author Ivan Rodriguez
 */
class PolicyScoringTest {

	@TempDir
	Path workDir;

	private SplitScore score(PolicyPrompt prompt, List<TraceCase> cases) {
		PolicyRun run = InnerLoopFixtures.run(PolicyCompiler.compile(prompt), cases, this.workDir);
		assertThat(run.ran()).as("the policy must run: " + run.diagnostic()).isTrue();
		return SplitScore.of("heldout", cases, run.verdicts());
	}

	@Test
	void aMixedPolicyYieldsTwoNumbersAndTheyDiffer() {
		PolicyPrompt mixed = PolicyPrompt.parse(InnerLoopFixtures.MIXED_PROMPT);

		SplitScore score = score(mixed, InnerLoopFixtures.heldout());

		// Wrong both ways: the keyword exclusion loses the decoy and the below-baseline
		// trigger, and dropping the lifecycle exclusion invents a defect out of a policy
		// block.
		assertThat(score.falsePositives()).isPositive();
		assertThat(score.falseNegatives()).isPositive();
		assertThat(score.precision()).isLessThan(1.0).isPositive();
		assertThat(score.recall()).isLessThan(1.0).isPositive();
		assertThat(score.precision()).isNotEqualTo(score.recall());
		assertThat(score.describe()).contains("precision").contains("recall");
	}

	@Test
	void theScoreCarriesTwoNumbersAndNoThirdOneToBlendInto() {
		// A count of the double-valued components, not a blacklist of names: a blacklist
		// is
		// defeated by calling the blend something other than "f1".
		List<String> doubles = List.of(SplitScore.class.getRecordComponents())
			.stream()
			.filter((component) -> component.getType() == double.class)
			.map(RecordComponent::getName)
			.sorted()
			.toList();

		assertThat(doubles).containsExactlyInAnyOrder("precision", "recall");
	}

	@Test
	void theHeldOutSplitReportsTheTwoNumbersApartForThePolicyThatTradesThem() {
		List<TraceCase> heldout = InnerLoopFixtures.heldout();

		SplitScore handWritten = SplitScore.of("heldout", heldout,
				InnerLoopFixtures.run(DetectionPolicy.handWritten(), heldout, this.workDir).verdicts());
		PolicyRun keyword = InnerLoopFixtures.run(
				PolicyGenerator
					.emit(PolicyPrompt.frozenSeed(), PolicyPrompt.frozenSeed().name(), 1,
							PolicyGenerator.MutationOperator.KEYWORD_INSTEAD_OF_PREFIX)
					.script(),
				heldout, this.workDir);
		SplitScore traded = SplitScore.of("heldout", heldout, keyword.verdicts());

		assertThat(handWritten.precision()).isEqualTo(1.0);
		assertThat(handWritten.recall()).isEqualTo(1.0);
		// The decoy is the whole cost of the keyword: it matches the two real defects
		// whose
		// messages mention the tier, and nothing else changes. Reporting one number would
		// have
		// called this policy nearly as good as the hand-written one.
		assertThat(traded.precision()).isEqualTo(1.0);
		assertThat(traded.recall()).isLessThan(1.0);
	}

	@Test
	void anUnansweredLineIsNotScoredAsACleanRejection() {
		List<TraceCase> heldout = InnerLoopFixtures.heldout();
		List<PolicyRun.Verdict> oneShort = new ArrayList<>(
				InnerLoopFixtures.run(DetectionPolicy.handWritten(), heldout, this.workDir).verdicts());
		oneShort.remove(oneShort.size() - 1);

		assertThatIllegalArgumentException().isThrownBy(() -> SplitScore.of("heldout", heldout, oneShort))
			.withMessageContaining("never answered");
	}

	@Test
	void aVerdictForALineThatIsNotInTheSplitIsRefused() {
		List<TraceCase> heldout = InnerLoopFixtures.heldout();
		List<PolicyRun.Verdict> verdicts = new ArrayList<>(
				InnerLoopFixtures.run(DetectionPolicy.handWritten(), heldout, this.workDir).verdicts());
		verdicts.add(PolicyRun.Verdict.clean("t-001"));

		assertThatIllegalArgumentException().isThrownBy(() -> SplitScore.of("heldout", heldout, verdicts))
			.withMessageContaining("not in it");
	}

	@Test
	void theScriptIsNeverHandedTheLabelItIsScoredAgainst() throws Exception {
		JsonNode rendered = new ObjectMapper().readTree(PolicyInput.stdinJson(InnerLoopFixtures.heldout()));

		Set<String> evidence = Set.of("runId", "success", "error", "steps", "pack", "spec_context");
		List<String> leaks = new ArrayList<>();
		rendered.get("cases").forEach((node) -> {
			List<String> fields = new ArrayList<>();
			node.fieldNames().forEachRemaining(fields::add);
			if (!evidence.containsAll(fields)) {
				leaks.add(node.get("runId").asText() + " carries " + fields);
			}
		});
		assertThat(leaks).isEmpty();
		assertThat(rendered.get("cases")).hasSize(InnerLoopFixtures.heldout().size());
		assertThat(PolicyInput.stdinJson(InnerLoopFixtures.heldout())).doesNotContain("\"label\"")
			.doesNotContain("label_reason");
	}

}
