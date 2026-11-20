/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.core.PromptData;
import com.embabel.smith.builder.core.SpecPromptCaller;

/**
 * Produces the {@link EvalSuite} the skill build validates against. The builder is given
 * a free-form description rather than a known blueprint, so the suite is drafted by the
 * model from that description (the skill-creator's "draft some quantitative evals" step)
 * and published beside the skill.
 *
 * @author Ivan Rodriguez
 */
public final class EvalSource {

	private EvalSource() {
	}

	/**
	 * Asks the model to draft a suite from the description.
	 * @param caller the model-call seam.
	 * @param prompt the eval-drafting system prompt.
	 * @param skillName the skill name the suite is written for.
	 * @param description the business-process description.
	 * @return the parsed suite.
	 */
	public static EvalSuite generate(SpecPromptCaller caller, String prompt, String skillName, String description) {
		String user = "Below is a business-process description. Create the evals.json suite for the skill named '"
				+ skillName + "'.\n\n" + PromptData.block("document", description);
		return LlmJson.callAndParse(caller, prompt, user, 3, "the generated evals", EvalSuite::parse);
	}

}
