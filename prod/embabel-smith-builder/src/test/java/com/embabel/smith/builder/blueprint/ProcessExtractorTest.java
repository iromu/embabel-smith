/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import com.embabel.smith.builder.core.SpecPromptCaller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the model-based process extraction: a stub {@link SpecPromptCaller}
 * returns a fixed description, and the extractor returns it verbatim while threading the
 * discovery prompt through as the system message.
 */
class ProcessExtractorTest {

	@Test
	void returnsTheModelDescriptionVerbatim() {
		String answer = "# Business Overview\nThe application processes orders.";
		ProcessExtractor extractor = new ProcessExtractor((system, user) -> answer, "DISCOVERY-PROMPT");
		assertThat(extractor.extract("some docs corpus")).isEqualTo(answer);
	}

	@Test
	void threadsTheDiscoveryPromptAsTheSystemMessage() {
		String[] seen = new String[2];
		ProcessExtractor extractor = new ProcessExtractor((system, user) -> {
			seen[0] = system;
			seen[1] = user;
			return "description";
		}, "DISCOVERY-PROMPT");
		extractor.extract("corpus body");
		assertThat(seen[0]).isEqualTo("DISCOVERY-PROMPT");
		assertThat(seen[1]).contains("corpus body");
	}

	@Test
	void handsEachCorpusShapeItsOwnTag() {
		String[] seen = new String[1];
		ProcessExtractor extractor = new ProcessExtractor((system, user) -> {
			seen[0] = user;
			return "description";
		}, "DISCOVERY-PROMPT");

		extractor.extract("a docs corpus");
		assertThat(seen[0]).contains("<documents>\na docs corpus\n</documents>");

		extractor.extractCodebase("a code corpus");
		assertThat(seen[0]).contains("<codebase>\na code corpus\n</codebase>");
	}

	@Test
	void anExtractorWithoutATemplateRunsTheBundledDiscoveryPrompt() {
		String[] seen = new String[1];
		ProcessExtractor extractor = new ProcessExtractor((system, user) -> {
			seen[0] = system;
			return "description";
		});

		assertThat(extractor.extract("corpus body")).isEqualTo("description");
		assertThat(seen[0]).contains("# Extract the Business Process from This Codebase");
	}

}
