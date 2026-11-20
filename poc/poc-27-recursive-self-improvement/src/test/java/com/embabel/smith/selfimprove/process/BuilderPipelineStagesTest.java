/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.3, offline half: the stage list this POC measures a generated process against is
 * the builder's own, taken from {@code docs/08-builder.md} rather than summarised here,
 * and the coverage check reports a dropped stage when one is dropped.
 *
 * <p>
 * Offline. The spec sets are hand-authored, so what is being tested is the coverage rule
 * and the stage list's agreement with the documentation — neither of which needs a model.
 * The live half of 5.3 runs the same rule over what a model produced.
 *
 * @author Ivan Rodriguez
 */
class BuilderPipelineStagesTest {

	/**
	 * Every stage of {@link BuilderPipelineStages}, spelled the way the self-description
	 * spells them, in build order.
	 */
	private static final String[] ALL_STAGES = { "generate", "validate", "write", "evaluate", "inspect",
			"security-scan", "lifecycle-stamp", "publish" };

	@Test
	void everyStageIsGroundedInWordsTheBuilderDocumentationActuallyPrints() {
		// A stage this POC invented would have no phrase to quote, and a docs page that
		// renamed a stage would otherwise leave this list describing a pipeline that no
		// longer exists. Whitespace is collapsed by the check, so markdown line wrapping
		// in
		// the page does not break an anchor.
		assertThat(BuilderPipelineStages.anchorsMissingFrom(ProcessFixtures.builderDocs()))
			.as("stages docs/08-builder.md does not document")
			.isEmpty();
	}

	@Test
	void theStageListIsTheOrderTheBuilderHoldsThemIn() {
		// The self-description's own numbering: generate, validate, write, evaluate,
		// inspect,
		// security scan, lifecycle stamp, publish. The order matters because a process
		// that
		// names all eight in the wrong order is a different claim from one that drops
		// two.
		assertThat(BuilderPipelineStages.ids()).containsExactly("generate", "validate", "write", "evaluate", "inspect",
				"security scan", "lifecycle stamp", "publish");
	}

	@Test
	void aProcessNamingEveryStageDropsNone() {
		var coverage = BuilderPipelineStages.coverage(ProcessFixtures.stagesProcess(ALL_STAGES));

		assertThat(coverage.dropped()).isEmpty();
		assertThat(coverage.named()).isEqualTo(BuilderPipelineStages.ids());
	}

	@Test
	void aProcessThatDropsStagesRecordsExactlyWhichOnes() {
		// The control for the test above: without it, a coverage rule that matched
		// everything
		// would pass it for the wrong reason. Here the model wrote the four stages that
		// cost a
		// model call and the publisher, and lost the two gates that cost nothing.
		var coverage = BuilderPipelineStages
			.coverage(ProcessFixtures.stagesProcess("generate", "validate", "write", "evaluate", "publish"));

		assertThat(coverage.named()).containsExactly("generate", "validate", "write", "evaluate", "publish");
		assertThat(coverage.dropped()).containsExactly("inspect", "security scan", "lifecycle stamp");
	}

}
