/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import java.util.List;

import com.embabel.smith.builder.cli.BuildFlow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The result carries the flow that produced it and the report prints it, which is the
 * last clause {@code builder-executes-its-own-packs} leaves open: a run has to be
 * attributable from what it printed rather than from how it was launched, and with four
 * flows that is not a detail.
 *
 * <p>
 * The stamp itself is the CLI's ({@code cli/BuildCommand} puts the flow on the result it
 * got back, since {@code BuildOptions} deliberately has no flow field), so the third case
 * here is the honest absence: a result nobody stamped prints no flow line rather than
 * guessing {@code java}. The CLI end of the same claim — that a {@code smith build} run
 * prints it — is {@code cli/BuildCommandTest}.
 *
 * @author Ivan Rodriguez
 */
class BuildResultFlowTest {

	@Test
	void aBuiltResultNamesTheFlowThatBuiltIt() {
		BuildResult result = BuildResult.built("pack", 1.0, null, null, null).withFlow(BuildFlow.HARNESS);

		assertThat(result.flow()).isEqualTo(BuildFlow.HARNESS);
		assertThat(result.report()).contains("BUILD OK: pack").contains("flow:        harness");
	}

	@Test
	void aBlockedResultNamesTheFlowTooBecauseABlockedRunStillHasToBeAttributable() {
		BuildResult result = BuildResult.blocked("eval", "pack", List.of("below the score gate"), 0.4, null, null)
			.withFlow(BuildFlow.AGENTIC);

		assertThat(result.report()).contains("BUILD BLOCKED at eval (pack)")
			.contains("flow:        agentic")
			.contains("below the score gate");
	}

	@Test
	void aResultNobodyStampedSaysNothingAboutTheFlow() {
		assertThat(BuildResult.built("pack", 1.0, null, null, null).flow()).isNull();
		assertThat(BuildResult.built("pack", 1.0, null, null, null).report()).doesNotContain("flow:");
	}

	/**
	 * The stamp is a copy, not a mutation: the same result handed to two drivers cannot
	 * disagree about what it says.
	 */
	@Test
	void stampingOneResultLeavesAnotherAlone() {
		BuildResult plain = BuildResult.built("pack", 1.0, null, null, null);

		BuildResult harness = plain.withFlow(BuildFlow.HARNESS);

		assertThat(plain.flow()).isNull();
		assertThat(harness.flow()).isEqualTo(BuildFlow.HARNESS);
		assertThat(harness.isBuilt()).as("the rest of the result came across unchanged").isTrue();
		assertThat(harness.packName()).isEqualTo("pack");
	}

}
