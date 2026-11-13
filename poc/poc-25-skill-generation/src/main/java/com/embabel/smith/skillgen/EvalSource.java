/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.skillgen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The two sources of an {@link EvalSuite}, mirroring the skill-creator's "save test cases to
 * {@code evals/evals.json}" plus its "draft some quantitative evals" step: a hand-authored
 * suite shipped as a resource, or a suite the model drafts from the blueprint at run time.
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
