package com.embabel.smith.studio.generate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the model-based process extraction: a stub {@link FlowPromptCaller}
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

}
