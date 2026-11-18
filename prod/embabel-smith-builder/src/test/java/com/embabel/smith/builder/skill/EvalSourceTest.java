/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.core.SpecPromptCaller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline test for the eval drafter: the suite comes back parsed, and the responsibility
 * it is drawn from reaches the model as delimited data rather than as prose beside the
 * instructions — no model and no endpoint needed.
 *
 * @author Ivan Rodriguez
 */
class EvalSourceTest {

	private static final String SUITE = """
			{"skill_name": "cold-store-goods-receipt", "evals": [{"id": 1, "prompt": "p",
			"expected_output": "o", "files": [], "expectations": ["must apply FEFO"]}]}
			""";

	@Test
	void theDescriptionArrivesInsideDocumentTags() {
		String[] seen = new String[1];
		SpecPromptCaller caller = (system, user) -> {
			seen[0] = user;
			return SUITE;
		};

		EvalSuite suite = EvalSource.generate(caller, "evals prompt", "cold-store-goods-receipt",
				"receive chilled pallets into cold store");

		assertThat(suite.evals()).hasSize(1);
		assertThat(seen[0]).contains("<document>\nreceive chilled pallets into cold store\n</document>");
	}

}
