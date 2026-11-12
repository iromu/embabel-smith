package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SpecEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link LexicalSelector} returns the correct top-1 id on a small candidate set:
 * the spec whose words best match the query wins, and an empty set yields no selection.
 */
class LexicalSelectorTest {

	private final LexicalSelector selector = new LexicalSelector();

	private static SpecEntry entry(String id, String description) {
		return new SpecEntry(id, id, description, List.of("UserInput"), "BackgroundMessage");
	}

	@Test
	void picksTheBestLexicalMatch() {
		List<SpecEntry> candidates = List.of(
				entry("coffee-machine",
						"Explains how a coffee machine works, including the boiler and the portafilter"),
				entry("toaster", "Explains how a toaster works"),
				entry("electric-kettle", "Explains how an electric kettle works"));
		assertThat(selector.select("explain how a coffee machine works", candidates)).isEqualTo("coffee-machine");
	}

	@Test
	void returnsNullForEmptyCandidateSet() {
		assertThat(selector.select("explain how a coffee machine works", List.of())).isNull();
	}

}
