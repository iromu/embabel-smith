/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The two sources of an {@link EvalSuite}, mirroring the skill-creator's "save test cases
 * to {@code evals/evals.json}" plus its "draft some quantitative evals" step: a
 * hand-authored suite shipped as a resource, or a suite the model drafts from the
 * blueprint at run time.
 *
 * @author Ivan Rodriguez
 */
public final class EvalSource {

	private EvalSource() {
	}

	/**
	 * Loads a hand-authored suite bundled under {@code /evals/<slug>/evals.json}.
	 * @param slug the blueprint slug (e.g. {@code aurora}).
	 * @return the parsed suite.
	 */
	public static EvalSuite load(String slug) {
		try (InputStream in = EvalSource.class.getResourceAsStream("/evals/" + slug + "/evals.json")) {
			if (in == null) {
				throw new IllegalStateException("No bundled evals for blueprint '" + slug + "'");
			}
			return EvalSuite.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read bundled evals for '" + slug + "'", ex);
		}
	}

	/**
	 * Asks the model to draft a suite from the blueprint.
	 * @param caller the model-call seam.
	 * @param prompt the eval-drafting system prompt.
	 * @param skillName the skill name the suite is written for.
	 * @param blueprint the business-process description.
	 * @return the parsed suite.
	 */
	public static EvalSuite generate(ChatCaller caller, String prompt, String skillName, String blueprint) {
		String user = "Below is a business-process description. Create the evals.json suite for the skill named '"
				+ skillName + "'.\n\n" + blueprint;
		return LlmJson.callAndParse(caller, prompt, user, 3, "the generated evals", EvalSuite::parse);
	}

}
