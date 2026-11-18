/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for cutting a discovery answer into deliverable documents: the heading
 * spellings a model actually produces all resolve to the same file, a subsection cannot
 * claim a deliverable from the heading that begins it, and an answer that names none of
 * them yields nothing rather than one unlabelled file.
 */
class BlueprintSplitterTest {

	private final BlueprintSplitter splitter = new BlueprintSplitter();

	@Test
	void splitsOnTheDeliverableHeadings() {
		Map<String, String> documents = this.splitter.split(ANSWER);

		assertThat(documents).containsOnlyKeys("BUSINESS_OVERVIEW.md", "ACTORS_AND_ROLES.md", "WORKFLOWS.md");
		assertThat(documents.get("BUSINESS_OVERVIEW.md")).startsWith("## BUSINESS_OVERVIEW.md")
			.contains("The app takes orders.")
			.doesNotContain("Who acts");
		assertThat(documents.get("ACTORS_AND_ROLES.md")).contains("Who acts").doesNotContain("Step by step");
	}

	@Test
	void everyHeadingSpellingResolvesToTheSameFile() {
		assertThat(deliverableUnder("## BUSINESS_PROCESSES.md")).isEqualTo("BUSINESS_PROCESSES.md");
		assertThat(deliverableUnder("## Business Processes")).isEqualTo("BUSINESS_PROCESSES.md");
		assertThat(deliverableUnder("## 3. business processes")).isEqualTo("BUSINESS_PROCESSES.md");
		assertThat(deliverableUnder("### Business_rules")).isEqualTo("BUSINESS_RULES.md");
	}

	@Test
	void anAmpersandHeadingMatchesTheUnderscoreName() {
		Map<String, String> documents = this.splitter.split("## States & Transitions\n\nThree states.\n");

		assertThat(documents).containsOnlyKeys("STATES_AND_TRANSITIONS.md");
		assertThat(documents.get("STATES_AND_TRANSITIONS.md")).contains("Three states.");
	}

	@Test
	void aSubsectionCannotStealADeliverableFromItsOwnHeading() {
		String answer = """
				## BUSINESS_OVERVIEW.md

				### Workflow steps are listed later.

				Overview body.

				## WORKFLOWS.md

				Step by step.
				""";

		Map<String, String> documents = this.splitter.split(answer);

		assertThat(documents.get("WORKFLOWS.md")).startsWith("## WORKFLOWS.md").contains("Step by step");
		assertThat(documents.get("BUSINESS_OVERVIEW.md")).contains("Workflow steps are listed later");
	}

	@Test
	void aHeadingInsideACodeFenceIsNotAMarker() {
		String answer = """
				## BUSINESS_OVERVIEW.md

				The answer template is:

				```markdown
				## WORKFLOWS.md
				```

				More overview body.

				## WORKFLOWS.md

				The real workflows.
				""";

		Map<String, String> documents = this.splitter.split(answer);

		assertThat(documents.get("WORKFLOWS.md")).contains("The real workflows").doesNotContain("```markdown");
	}

	@Test
	void anAnswerWithNoDeliverableHeadingsYieldsNoDocuments() {
		assertThat(this.splitter.split("# My project\n\nIt does things.")).isEmpty();
		assertThat(this.splitter.split(null)).isEmpty();
	}

	private static String deliverableUnder(String heading) {
		return new BlueprintSplitter().split(heading + "\n\nbody\n").keySet().iterator().next();
	}

	private static final String ANSWER = """
			Here is the business process I reconstructed.

			## BUSINESS_OVERVIEW.md

			The app takes orders.

			## Actors and Roles

			Who acts.

			## WORKFLOWS.md

			Step by step.
			""";

}
